package com.bighealth.entity;

import com.bighealth.service.GraphSearch;
import com.google.common.base.Stopwatch;
import com.bighealth.llm.KgCommunityTask;
import com.bighealth.llm.KgEntityDupRemovalTask;
import com.bighealth.llm.KgRelationshipDupRemovalTask;
import com.bighealth.repository.*;
import com.bighealth.service.DocumentLoader;
import com.bighealth.service.GraphBuilder;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jgrapht.Graph;
import org.jgrapht.alg.clustering.LabelPropagationClustering;
import org.jgrapht.alg.interfaces.ClusteringAlgorithm;
import org.jgrapht.graph.SimpleGraph;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static com.bighealth.service.GraphBuilder.BATCH_SIZE;

/**
 * Contains list of entities and relationships in the Knowledge Graph
 */
public class KGGraph {
    private static final Logger logger = LogManager.getLogger(KGGraph.class.getSimpleName());

    private String schema;
    private List<KGSegment> segments;
    // map of entities
    private Map<String, KGEntity> entityMap;

    private Map<KGRelationshipKey, KGRelationship> relationshipMap;

    private Map<String, KGCommunity> communityMap;

    public KGGraph(String schema) {
        this.schema = schema;
        this.segments = new ArrayList<>();
        this.entityMap = new HashMap<>();
        this.relationshipMap = new HashMap<>();
        this.communityMap = new HashMap<>();
    }

    public String getSchema() {
        return schema;
    }

    public void addSegment(KGSegment segment) {
        this.segments.add(segment);
    }

    public KGEntity getEntity(String name) {
        return entityMap.get(name);
    }

    public void addEntity(KGEntity entity) {
        KGEntity currentEntity = this.entityMap.get(entity.getName());
        if (currentEntity != null) {
            currentEntity.merge(entity);
            return;
        }
        this.entityMap.put(entity.getName(), entity);
    }

    public int entitySize() {
        return entityMap.size();
    }

    public int relationshipSize() {
        return relationshipMap.size();
    }

    public int communitySize() {
        return communityMap.size();
    }

    public int segmentSize() {
        return segments.size();
    }

    public void addRelationship(KGRelationship relationship) {
        KGRelationship currentRelationship = this.relationshipMap.get(relationship.getId());
        if (currentRelationship != null) {
            currentRelationship.merge(relationship);
            return;
        }
        this.relationshipMap.put(relationship.getId(), relationship);
    }

    public void addCommunity(KGCommunity community) {
        this.communityMap.put(community.getName(), community);
    }

    public Map<String, KGEntity> getEntityMap() {
        return entityMap;
    }

    public Map<KGRelationshipKey, KGRelationship> getRelationshipMap() {
        return relationshipMap;
    }

    public Map<String, KGCommunity> getCommunityMap() {
        return communityMap;
    }

    public List<KGSegment> getSegments() {
        return segments;
    }

    public void addEntities(List<String[]> entities) {
        for (String[] entity : entities) {
            if (entity.length < 3) {
                continue;
            }
            String name = entity[0].trim();
            String type = entity[1].trim();
            String description = entity[2].trim();
            KGEntity kgEntity = new KGEntity(name, type, description);
            addEntity(kgEntity);
        }
    }

    public void addRelationships(List<String[]> relationships) {
        for (String[] relationship : relationships) {
            if (relationship.length < 4) {
                continue;
            }
            String source = relationship[0].trim();
            String target = relationship[1].trim();
            String type = relationship[2].trim();
            String description = relationship[3].trim();
            KGRelationship kgRelationship =
                    new KGRelationship(source, target, type, description);
            addRelationship(kgRelationship);
        }
    }

    public Graph toGraph() {
        Graph<KGEntity, KGRelationship> graph = new SimpleGraph<>(KGRelationship.class);
        for (KGEntity entity : entityMap.values()) {
            graph.addVertex(entity);
        }
        for (KGRelationship relationship : relationshipMap.values()) {
            KGEntity source = entityMap.get(relationship.getSource());
            KGEntity target = entityMap.get(relationship.getTarget());
            if (source == null || target == null) {
                logger.info("Source or target not found for relationship: {}, source: {}/{}, " +
                        "target: {}/{}", relationship, relationship.getSource(), source,
                        relationship.getTarget(), target);
                continue;
            }
            if (source.equals(target)) {
                logger.info("Skipping self-loop for relationship: {}, source: {}, target: {}",
                        relationship, source.getName(), target.getName());
                continue;
            }
            logger.info("Adding edge from {} to {} with relationship {}",
                    source.getName(), target.getName(), relationship);
            graph.addEdge(source, target, relationship);
        }
        return graph;
    }

    public void buildCommunities(GraphBuilder graphBuilder) {
        logger.info("Building communities...");
        Stopwatch watch = Stopwatch.createStarted();
        Graph<KGEntity, KGRelationship> graph = toGraph();
        List<KgCommunityTask> taskList = getKgCommunityTasks(graphBuilder, graph);
        List<CommunityData> dataList = batchGetCommunityData(
                graphBuilder.getExecutorService(), taskList);
        for (CommunityData data : dataList) {
            if (data == null) {
                continue;
            }
            KGCommunity community = new KGCommunity(data.getName(), data.getSummary());
            addCommunity(community);
        }
        logger.info("Building communities took {} ms, community count: {}",
                watch.elapsed().toMillis(), communityMap.size());
    }

    @NotNull
    private List<KgCommunityTask> getKgCommunityTasks(GraphBuilder graphBuilder,
                                                      Graph<KGEntity,
                                                              KGRelationship> graph) {
        LabelPropagationClustering<KGEntity, KGRelationship> clustering =
                new LabelPropagationClustering<>(graph);
        ClusteringAlgorithm.Clustering<KGEntity> c = clustering.getClustering();
        List<Set<KGEntity>> clusters = c.getClusters();
        List<KgCommunityTask> taskList = new ArrayList<>();
        String communityTemplate = graphBuilder.getCommunityTemplate();
        for (Set<KGEntity> cluster : clusters) {
            if (cluster.size() < 2 || cluster.size() >= 500) {
                continue;
            }
            KgCommunityTask task = new KgCommunityTask(communityTemplate,
                    this, graph, cluster,
                    graphBuilder.getModel());
            taskList.add(task);
        }
        return taskList;
    }

    private List<CommunityData> batchGetCommunityData(
            ExecutorService executorService,
            List<KgCommunityTask> taskList) {
        try {
            List<CommunityData> dataList = new ArrayList<>();
            int batchSize = BATCH_SIZE;
            List<Future<CommunityData>> futureList = new ArrayList<>();
            int index = 0;
            for (KgCommunityTask task : taskList) {
                futureList.add(executorService.submit(task));
                index++;
                if (futureList.size() >= batchSize) {
                    Stopwatch watch = Stopwatch.createStarted();
                    List<Future<CommunityData>> subList = futureList.subList(0, futureList.size());
                    for (Future<CommunityData> future : subList) {
                        dataList.add(future.get());
                    }
                    logger.info("Batch get community data took {} ms, index: {}/{}",
                            watch.elapsed().toMillis(), index, taskList.size());
                    futureList.clear();
                }
            }
            if (futureList.isEmpty()) {
                return dataList;
            }
            Stopwatch watch = Stopwatch.createStarted();
            for (Future<CommunityData> future : futureList) {
                dataList.add(future.get());
            }
            logger.info("Batch get community data took {} ms, index: {}/{}",
                    watch.elapsed().toMillis(), index, taskList.size());
            return dataList;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public void save(GraphBuilder graphBuilder) {
        saveSegments(graphBuilder);
        saveEntities(graphBuilder);
        saveRelationships(graphBuilder);
        saveCommunities(graphBuilder);
    }

    private void saveSegments(GraphBuilder graphBuilder) {
        logger.info("Saving segments...");
        Stopwatch watch = Stopwatch.createStarted();
        JdbcRepository repository = graphBuilder.getJdbcRepository();
        repository.saveAllKGSegments(schema, segments);
        logger.info("Saving segments took {} ms, size: {}",
                watch.elapsed().toMillis(), segments.size());

    }

    private void saveEntities(GraphBuilder graphBuilder) {
        logger.info("Saving entities...");
        Stopwatch watch = Stopwatch.createStarted();
        Collection<KGEntity> entities = entityMap.values();
        List<KGEntity> mergedEntityList = new ArrayList<>();
        List<KGEntity> entityList = new ArrayList<>();
        JdbcRepository e = graphBuilder.getJdbcRepository();
        for (KGEntity entity : entities) {
            KGEntity currentEntity = e.findKGEntityById(schema, entity.getName());
            if (currentEntity != null) {
                entity.merge(currentEntity);
                logger.info("delete entity: {}", currentEntity.getName());
                graphBuilder.getJdbcRepository().deleteKGEntityById(schema, currentEntity.getName());
            }
            if (entity.isMerged()) {
                mergedEntityList.add(entity);
            } else {
                entityList.add(entity);
            }
        }
        graphBuilder.getJdbcRepository().saveAllKGEntities(schema, entityList);
        logger.info("Saving entities took {} ms, size: {}",
                watch.elapsed().toMillis(), entityList.size());
        if (mergedEntityList.isEmpty()) {
            return;
        }
        watch = Stopwatch.createStarted();
        DocumentLoader documentLoader = graphBuilder.getDocumentLoader();
        String template = documentLoader.readKgDupRemovalPrompt();
        ChatLanguageModel model = graphBuilder.getModel();
        List<Callable> taskList = new ArrayList<>();
        for (KGEntity entity : mergedEntityList) {
            KgEntityDupRemovalTask task = new KgEntityDupRemovalTask(
                    template, entity, model);
            taskList.add(task);
        }
        graphBuilder.batchExecuteTasks(taskList);
        graphBuilder.getJdbcRepository().saveAllKGEntities(schema, mergedEntityList);
        logger.info("Saving entities took {} ms, size: {}",
                watch.elapsed().toMillis(), mergedEntityList.size());
    }

    private void saveRelationships(GraphBuilder graphBuilder) {
        logger.info("Saving relationships...");
        Stopwatch watch = Stopwatch.createStarted();
        Collection<KGRelationship> relationships = relationshipMap.values();
        List<KGRelationship> mergedRelationshipList = new ArrayList<>();
        List<KGRelationship> relationshipList = new ArrayList<>();
        JdbcRepository repository = graphBuilder.getJdbcRepository();
        for (KGRelationship relationship : relationships) {
            KGRelationship currentRelationship =
                    repository.findKGRelationshipById(schema, relationship.getId());
            if (currentRelationship != null) {
                relationship.merge(currentRelationship);
                logger.info("delete relationship: {}", currentRelationship.getId());
                repository.deleteKGRelationshipById(schema, currentRelationship.getId());
            }
            if (relationship.isMerged()) {
                mergedRelationshipList.add(relationship);
            } else {
                relationshipList.add(relationship);
            }
        }
        repository.saveAllKGRelationships(schema, relationshipList);
        logger.info("Saving relationships took {} ms, size: {}",
                watch.elapsed().toMillis(), relationshipList.size());
        if (mergedRelationshipList.isEmpty()) {
            return;
        }
        watch = Stopwatch.createStarted();
        DocumentLoader documentLoader = graphBuilder.getDocumentLoader();
        List<Callable> taskList = new ArrayList<>();
        String template = documentLoader.readKgDupRemovalPrompt();
        ChatLanguageModel model = graphBuilder.getModel();
        for (KGRelationship relationship : mergedRelationshipList) {
            KgRelationshipDupRemovalTask task = new KgRelationshipDupRemovalTask(
                    template, relationship, model);
            taskList.add(task);
        }
        graphBuilder.batchExecuteTasks(taskList);
        repository.saveAllKGRelationships(schema, mergedRelationshipList);
        logger.info("Saving merged relationships took {} ms, size: {}",
                watch.elapsed().toMillis(), mergedRelationshipList.size());
    }

    private void saveCommunities(GraphBuilder graphBuilder) {
        logger.info("Saving communities...");
        Stopwatch watch = Stopwatch.createStarted();
        Collection<KGCommunity> communities = communityMap.values();
        List<KGCommunity> communityList = new ArrayList<>(communities);
        JdbcRepository repository = graphBuilder.getJdbcRepository();
        repository.saveAllKGCommunities(schema, communityList);
        logger.info("Saving communities took {} ms, size: {}",
                watch.elapsed().toMillis(), communityList.size());

    }

    public void load(GraphSearch builder) {
        logger.info("Loading graph for schema: {} ...", schema);
        Stopwatch watch = Stopwatch.createStarted();
        JdbcRepository repository = builder.getJdbcRepository();
        List<KGSegment> segments = repository.getAllSegments(schema);
        for (KGSegment segment : segments) {
            addSegment(segment);
        }
        List<KGEntity> entities = repository.getAllEntities(schema);
        for (KGEntity entity : entities) {
            addEntity(entity);
        }
        List<KGRelationship> relationships = repository.getAllRelationships(schema);
        for (KGRelationship relationship : relationships) {
            addRelationship(relationship);
        }
        List<KGCommunity> communities = repository.getAllCommunities(schema);
        for (KGCommunity community : communities) {
            addCommunity(community);
        }
        logger.info("Loading graph for schema {} took {} ms, entitySize: {}, " +
                        "relationshipSize: {}, communitySize: {}, segmentSize: {}",
                schema, watch.elapsed().toMillis(), entitySize(), relationshipSize(),
                communitySize(), segmentSize());
    }

    public KGEntity getEntityById(String id) {
        return entityMap.get(id);
    }

    public List<KGRelationship> getRelationships(String id) {
        List<KGRelationship> relationships = new ArrayList<>();
        for (KGRelationship relationship : relationshipMap.values()) {
            if (relationship.getSource().equals(id) || relationship.getTarget().equals(id)) {
                relationships.add(relationship);
            }
        }
        return relationships;
    }

}
