package com.bighealth.service;

import com.google.common.base.Stopwatch;
import com.bighealth.entity.KGEntity;
import com.bighealth.entity.KGGraph;
import com.bighealth.entity.KGSegment;
import com.bighealth.llm.KgTask;
import com.bighealth.llm.LLMModel;
import com.bighealth.repository.*;
import com.bighealth.util.FileConverter;
import com.bighealth.util.JsonArrayToStringArray;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class GraphBuilder {
    private static final Logger logger = java.util.logging.Logger.getLogger(GraphBuilder.class.getSimpleName());

    private final LLMModel llmModel;
    private final DocumentLoader documentLoader;
    private final CPMLoader cpmLoader;
    private final JdbcRepository jdbcRepository;

    private ExecutorService executorService;
    private String ragKgSystemPrompt;
    private String ragKgUserPrompt;
    private ChatLanguageModel model;

    public static final int BATCH_SIZE = 4;

    @Autowired
    public GraphBuilder(LLMModel llmModel, DocumentLoader documentLoader, CPMLoader cpmLoader,
                        JdbcRepository jdbcRepository) {
        this.llmModel = llmModel;
        this.documentLoader = documentLoader;
        this.cpmLoader = cpmLoader;
        this.jdbcRepository = jdbcRepository;
        this.executorService = Executors.newFixedThreadPool(BATCH_SIZE);
        this.model = llmModel.buildKgModel();
        initGraphs();
        try {
            this.ragKgSystemPrompt = documentLoader.readKgSystemPrompt();
            this.ragKgUserPrompt = documentLoader.readKgUserPrompt();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public LLMModel getLlmModel() {
        return llmModel;
    }

    public ChatLanguageModel getModel() {
        return model;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    private void initGraphs() {
    }

    public void buildAllGraphs() {
        for (String schema : Schemas.allSchemas()) {
            buildGraph(schema);
        }
    }

    @Transactional
    public void buildGraph(String schema) {
        logger.log(Level.INFO, "Building knowledge graph for schema: {0}", schema);
        switch (schema) {
            case Schemas.CPM -> {
                buildCPMGraph();
            }
            default -> {
                String directoryName = Schemas.getSchemaDir(schema);
                buildGraph(schema, directoryName);
            }
        }
    }



    public String getCommunityTemplate() {
        try {
            return documentLoader.readKgCommunityPrompt();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }


    private void buildGraph(String schemaName, List<String> slices, String description) {
        logger.log(Level.INFO, "Building knowledge graph for schema: {0}, description: {1}", new Object[]{schemaName, description});
        KGGraph graph = new KGGraph(schemaName);
        // add slices to segments
        for (String slice : slices) {
            KGSegment segment = new KGSegment(slice);
            graph.addSegment(segment);
        }
        List<KgTask> taskList = new ArrayList<>();
        int batchSize = BATCH_SIZE;
        int size = 0;
        for (String slice : slices) {
            taskList.add(new KgTask(SystemMessage.systemMessage(ragKgSystemPrompt),
                    ragKgSystemPrompt, ragKgUserPrompt,
                    TextSegment.from(slice), model));
            if (taskList.size() >= batchSize) {
                size += taskList.size();
                logger.log(Level.INFO, "Processing batch of size: {0}, {1}/{2}",
                        new Object[] {taskList.size(), size, slices.size()});
                List<String> textList = batchGetKgText(taskList);
                for (String text : textList) {
                    updateKnowledgeGraph(graph, text);
                }

                taskList.clear();
            }
        }
        if (!taskList.isEmpty()) {
            logger.log(Level.INFO, "Processing batch of size: {0}, {1}/{2}",
                    new Object[] {taskList.size(), size, slices.size()});
            List<String> textList = batchGetKgText(taskList);
            for (String text : textList) {
                updateKnowledgeGraph(graph, text);
            }
        }
        logger.log(Level.INFO, "Building communities for schema: {0}, size: {1}",
                new Object[] {schemaName, graph.size()});
        graph.buildCommunities(this);
        graph.save(this);
        jdbcRepository.saveKGFile(schemaName, description);
        logger.log(Level.INFO, "Finished building knowledge graph for: {0}/{1}",
                new Object[] {schemaName, description});
    }

    private void buildCPMGraph() {
        try {
            String description = "新编国家中成药第3版";
            String existFile = jdbcRepository.findKGFileByName(Schemas.CPM, description);
            if (existFile != null) {
                return;
            }
            List<String> nameList = new ArrayList<>();
            List<String> sliceList = new ArrayList<>();
            cpmLoader.collectSlices(nameList, sliceList);
            KGGraph graph = new KGGraph(Schemas.CPM);
            addAllDirect(graph, "中成药", nameList, sliceList);
            graph.save(this);
            jdbcRepository.saveKGFile(Schemas.CPM, description);
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Failed to build CPM graph", t);
        }
    }

    public void buildEBMGraph() {
        String directoryName = "指南共识篇";
        buildGraph(Schemas.EBM, directoryName);
    }

    public void buildTCMGraph() {
        String directoryName = "中医辩证";
        buildGraph(Schemas.TCM, directoryName);
    }

    public void buildNUTRGraph() {
        String directoryName = "营养医学";
        buildGraph(Schemas.NUTR, directoryName);
    }

    public void buildIMMGraph() {
        String directoryName = "免疫健康";
        buildGraph(Schemas.IMM, directoryName);
    }
    public void buildORGANGraph() {
        String directoryName = "器官功能";
        buildGraph(Schemas.ORGAN, directoryName);
    }
    public void buildAGINGGraph() {
        String directoryName = "衰老与退化";
        buildGraph(Schemas.AGING, directoryName);
    }

    private void buildGraph(String schemaName, String directoryName) {
        try {
            Path path = FileConverter.getResourcePath(directoryName);
            buildGraph(schemaName, path);
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Failed to build EBM graph", t);
        }
    }

    public void buildGraph(String schema, Path path) {
        if (Files.isDirectory(path)) {
            try {
                Files.list(path).forEach(p -> buildGraph(schema, p));
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "Failed to build graph", t);
            }
        } else {
            try {
                String description = path.getFileName().toString();
                String existFile = jdbcRepository.findKGFileByName(schema, description);
                if (existFile != null) {
                    logger.log(Level.INFO, "File already ingested: {0}", description);
                    return;
                }
                logger.log(Level.INFO, "Processing file: {0}", description);
                String text = FileConverter.convertFileToText(Files.newInputStream(path),
                        description);
                boolean direct = description.startsWith("slice") && description.endsWith(".txt")
                        || description.endsWith(".json");
                if (direct) {
                    logger.log(Level.INFO, "Directly ingesting file: {0}", description);
                    ingestSlices(schema, Collections.singletonList(text), description);
                } else {
                    // slip into chunks with overlap
                    String[] texts = documentLoader.splitText(text);
                    logger.log(Level.INFO, "Splitting file into {0} chunks, file size: {1}",
                            new Object[] {texts.length, text.length()});
                    ingestSlices(schema, Arrays.asList(texts), description);
                }

            } catch (Throwable t) {
                logger.log(Level.SEVERE, "Failed to build graph", t);
            }
        }
    }


    public void addAllDirect(KGGraph graph,
                             String type,
                             List<String> names,
                             List<String> slices)
    {
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            KGEntity entity = jdbcRepository.findKGEntityById(graph.getSchema(), name);
            if (entity != null) {
                continue;
            }
            String slice = slices.get(i);
            entity = new KGEntity(name, type, slice);
            graph.addEntity(entity);
            KGSegment segment = new KGSegment(slice);
            graph.addSegment(segment);
        }
    }

    public void ingestSlices(String schemaName, List<String> slices, String description) {
        List<String> textList = new ArrayList<>();
        if (!description.endsWith("json")) {
            for (String slice : slices) {
                textList.add(slice);
            }
        } else {
            logger.log(Level.INFO, "Ingesting json file: {0}", description);
            for (String slice : slices) {
                String[] jsonElements = JsonArrayToStringArray.convert(slice);
                for (String s : jsonElements) {
                    textList.add(s);
                }
            }
        }
        buildGraph(schemaName, textList, description);
    }


    private static final String ENTITIES_START = "<entities>";
    private static final String ENTITIES_END = "</entities";
    private static final String RELATIONSHIP_START = "<relationships>";
    private static final String RELATIONSHIP_END = "</relationships";
    private void updateKnowledgeGraph(KGGraph graph, String text) {
        try {
            if (text == null) {
                return;
            }
            logger.log(Level.INFO, "Updating knowledge graph with text: {0}", text);
            List<String[]> entities = getRecords(text, ENTITIES_START, ENTITIES_END);
            if (entities != null && !entities.isEmpty()) {
                logger.log(Level.INFO, "Found {0} entities", entities.size());
                graph.addEntities(entities);
            }
            List<String[]> relationships = getRecords(text, RELATIONSHIP_START, RELATIONSHIP_END);
            if (relationships != null && !relationships.isEmpty()) {
                logger.log(Level.INFO, "Found {0} relationships", relationships.size());
                graph.addRelationships(relationships);
            }
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Failed to update knowledge graph", t);
            t.printStackTrace();
        }
    }

    public static List<String[]> getRecords(String text, String startTag, String endTag) {
        try {
            int startIndex = text.indexOf(startTag);
            if (startIndex < 0) {
                return Collections.EMPTY_LIST;
            }
            startIndex = startIndex + startTag.length();
            int endIndex = text.indexOf(endTag);
            if (endIndex < 0) {
                endIndex = text.indexOf("```", startIndex);
            }
            if (endIndex < 0) {
                return Collections.EMPTY_LIST;
            }
            text = text.substring(startIndex, endIndex);
            BufferedReader br = new BufferedReader(new StringReader(text));
            String line = br.readLine();
            List<String[]> list = new ArrayList<>();
            while (line != null) {
                if (line.contains("|")) {
                    list.add(line.split("\\|"));
                }
                line = br.readLine();
            }
            return list;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
    private List<String> batchGetKgText(List<KgTask> taskList) {
        try {
            Stopwatch watch = Stopwatch.createStarted();
            List<String> textList = new ArrayList<>();
            List<Future<String>> futureList = new ArrayList<>();
            for (KgTask task : taskList) {
                futureList.add(executorService.submit(task));
            }
            for (Future<String> future : futureList) {
                textList.add(future.get());
            }
            logger.log(Level.INFO, "Batch get kg text, task size: {0}, time: {1}ms",
                    new Object[]{taskList.size(), watch.elapsed(TimeUnit.MILLISECONDS)});
            return textList;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public void batchExecuteTasks(List<Callable> taskList) {
        try {
            int batchSize = BATCH_SIZE;
            List<Future<String>> futureList = new ArrayList<>();
            int index = 0;
            for (Callable task : taskList) {
                futureList.add(executorService.submit(task));
                if (futureList.size() >= batchSize) {
                    Stopwatch watch = Stopwatch.createStarted();
                    for (Future<String> future : futureList) {
                        future.get();
                    }
                    index += futureList.size();
                    logger.log(Level.INFO, "Batch execute tasks, task size: {0}/{1}, time: {2}ms",
                            new Object[]{index, taskList.size(), watch.elapsed(TimeUnit.MILLISECONDS)});
                    futureList.clear();
                }
            }
            if (!futureList.isEmpty()) {
                Stopwatch watch = Stopwatch.createStarted();
                for (Future<String> future : futureList) {
                    future.get();
                }
                index += futureList.size();
                logger.log(Level.INFO, "Batch execute tasks, task size: {0}/{1}, time: {2}ms",
                        new Object[]{index, taskList.size(), watch.elapsed(TimeUnit.MILLISECONDS)});
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public DocumentLoader getDocumentLoader() {
        return documentLoader;
    }
    public JdbcRepository getJdbcRepository() {
        return jdbcRepository;
    }
}
