package com.bighealth.service;

import com.bighealth.entity.*;
import com.bighealth.llm.LLMModel;
import com.bighealth.llm.RagQuery;
import com.bighealth.llm.SessionData;
import com.bighealth.repository.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GraphSearch {
    public static final int MAX_COUNT = 32;
    public static final String HEALTH_START = "\n<健康数据>\n";
    public static final String HEALTH_END = "\n</健康数据>\n";
    private static final Logger logger = LoggerFactory.getLogger(GraphSearch.class.getSimpleName());

    @Autowired
    private JdbcRepository jdbcRepository;

    @Autowired
    private LLMModel llmModel;

    public static final int RELATIONSHIP_MAX_SIZE = 32;
    public static final int DOC_SEGMENT_MAX_SIZE = 6;

    public static final int ENTITY_MAX_SIZE = 24;

    private Map<String, KGGraph> graphMap = new HashMap<>();

    private SessionData sessionData;


    public GraphSearch() {

    }

    public void setSessionData(SessionData sessionData) {
        this.sessionData = sessionData;
    }

    @PostConstruct
    public void initialize() {
        loadGraphs();
    }

    public void loadGraphs() {
        String[] schemas = Schemas.SCHEMAS;
        for (String schema : schemas) {
            KGGraph graph = new KGGraph(schema);
            graph.load(this);
            graphMap.put(schema, graph);
        }
    }

    public int getMaxRelationshipSize(String schema) {
        switch (schema) {
            case Schemas.TCM:
            case Schemas.EBM:
                return RELATIONSHIP_MAX_SIZE * 2;
            default:
                break;
        }
        return RELATIONSHIP_MAX_SIZE;
    }

    public int getMaxEntitySize(String schema) {
        switch (schema) {
            case Schemas.TCM:
            case Schemas.EBM:
                return ENTITY_MAX_SIZE * 2;
            default:
                break;
        }
        return ENTITY_MAX_SIZE;
    }


    public String search(RagQuery query) {
        StringJoiner joiner = new StringJoiner("\n\n",
                HEALTH_START, HEALTH_END);
        String input = query.getQuery();
        String[] entities = query.getEntities();
        for (String schema : Schemas.SCHEMAS) {
            String result = search(schema, input, entities);
            joiner.add(result);
        }
        return joiner.toString();
    }

    public String queryForMoreSymptoms(RagQuery query) {
        StringJoiner joiner = new StringJoiner("\n\n",
                HEALTH_START, HEALTH_END);
        String input = query.getQuery();
        String[] entities = query.getEntities();
        String ebmResut = queryForMoreSymptoms(Schemas.EBM, input, entities);
        joiner.add(ebmResut);
        String tcmResult = queryForMoreSymptoms(Schemas.TCM, input, entities);
        joiner.add(tcmResult);
        return joiner.toString();
    }

    public String queryForHealthReport(RagQuery query) {
        StringJoiner joiner = new StringJoiner("\n\n",
                "生成综合健康分析报告:\n" + HEALTH_START, HEALTH_END);
        String input = query.getQuery();
        String[] entities = query.getEntities();
        if (entities == null || entities.length == 0) {
            return joiner.toString();
        }

        // EBM 西医
        String schema = Schemas.EBM;
        StringJoiner ebmJoiner = initializeSchemaJoiner(schema);
        addSegmentsToJoiner(schema, input, entities, ebmJoiner);// Default schema for health report
        List<String> ebmDiseases = queryForDiseases(schema, query);
        logger.info("西医: diseases={}", ebmDiseases);
        String healthReport = queryForHealthReport(schema, query, ebmDiseases.toArray(String[]::new));
        ebmJoiner.add(healthReport);
        joiner.add(ebmJoiner.toString());

        // TCM 中医
        schema = Schemas.TCM; // TCM schema for health report
        StringJoiner tcmJoiner = initializeSchemaJoiner(schema);
        addSegmentsToJoiner(schema, input, entities, tcmJoiner);
        List<String> tcmDiseases = queryForDiseases(schema, query);
        logger.info("中医: diseases={}", tcmDiseases);
        healthReport = queryForHealthReport(schema, query, tcmDiseases.toArray(String[]::new));
        tcmJoiner.add(healthReport);
        joiner.add(tcmJoiner.toString());

        ebmDiseases.addAll(tcmDiseases);

        // NUTR 营养
        schema = Schemas.NUTR;
        StringJoiner nutrJoiner = initializeSchemaJoiner(schema);
        addSegmentsToJoiner(schema, input, entities, nutrJoiner);
        healthReport = queryForHealthReport(schema, query, ebmDiseases.toArray(String[]::new));
        nutrJoiner.add(healthReport);
        joiner.add(nutrJoiner.toString());

        // CPM 中成药
        schema = Schemas.CPM;
        StringJoiner cpmJoiner = initializeSchemaJoiner(schema);
        addSegmentsToJoiner(schema, input, entities, cpmJoiner);
        healthReport = queryForHealthReport(schema, query, ebmDiseases.toArray(String[]::new));
        cpmJoiner.add(healthReport);
        joiner.add(cpmJoiner.toString());

        return joiner.toString();
    }

    public String queryForAssociatedFactorsReport(RagQuery query) {
        logger.info("queryForAssociatedFactorsReport: {}", query);
        StringJoiner joiner = new StringJoiner("\n\n",
                "生成关联因素的健康报告：\n" +HEALTH_START, HEALTH_END);
        String input = query.getQuery();
        String[] entities = query.getEntities();
        if (entities == null || entities.length == 0) {
            return joiner.toString();
        }

        // TCM 中医
        String schema = Schemas.TCM; // TCM schema for health report
        List<String> diseases = queryForDiseases(schema, query);

        // NUTR 营养
        schema = Schemas.NUTR;
        StringJoiner schemaJoiner = initializeSchemaJoiner(schema);
        addSegmentsToJoiner(schema, input, entities, schemaJoiner);
        String healthReport = queryForHealthReport(schema, query, diseases.toArray(String[]::new));
        schemaJoiner.add(healthReport);
        joiner.add(schemaJoiner.toString());

        // IMMU 免疫
        schema = Schemas.IMM;
        schemaJoiner = initializeSchemaJoiner(schema);
        addSegmentsToJoiner(schema, input, entities, schemaJoiner);
        healthReport = queryForHealthReport(schema, query, diseases.toArray(String[]::new));
        schemaJoiner.add(healthReport);
        joiner.add(schemaJoiner.toString());

        // ORG 组织器官
        schema = Schemas.ORGAN;
        schemaJoiner = initializeSchemaJoiner(schema);
        addSegmentsToJoiner(schema, input, entities, schemaJoiner);
        healthReport = queryForHealthReport(schema, query, diseases.toArray(String[]::new));
        schemaJoiner.add(healthReport);
        joiner.add(schemaJoiner.toString());

        // aging
        schema = Schemas.AGING;
        schemaJoiner = initializeSchemaJoiner(schema);
        addSegmentsToJoiner(schema, input, entities, schemaJoiner);
        healthReport = queryForHealthReport(schema, query, diseases.toArray(String[]::new));
        schemaJoiner.add(healthReport);
        joiner.add(schemaJoiner.toString());

        return joiner.toString();
    }

    public String queryForDrugInfo(RagQuery query) {
        StringJoiner joiner = new StringJoiner("\n\n",
                HEALTH_START, HEALTH_END);
        String input = query.getQuery();
        String[] entities = query.getEntities();
        if (entities == null || entities.length == 0) {
            return joiner.toString();
        }

        // CPM 中成药
        String schema = Schemas.CPM;
        addSegmentsToJoiner(schema, input, entities, joiner);
        List<KGEntity> entityList = searchEntities(schema, entities);
        if (!entityList.isEmpty()) {
            joiner.add(buildEntitiesContent(entityList));
        }
        return joiner.toString();
    }

    public String queryForGeneralHealthInfo(RagQuery query) {
        StringJoiner joiner = new StringJoiner("\n\n",
                HEALTH_START, HEALTH_END);
        String input = query.getQuery();
        String[] entities = query.getEntities();
        if (entities == null || entities.length == 0) {
            return joiner.toString();
        }
        for (String schema : Schemas.SCHEMAS) {
            StringJoiner schemaJoiner = initializeSchemaJoiner(schema);
            addSegmentsToJoiner(schema, input, entities, schemaJoiner);
            List<KGEntity> entityList = searchEntities(schema, entities);
            if (!entityList.isEmpty()) {
                schemaJoiner.add(buildEntitiesContent(entityList));
            }
            joiner.add(schemaJoiner.toString());
        }
        return joiner.toString();
    }


    private String queryForMoreSymptoms(String schema, String input, String[] entities) {
        StringJoiner joiner = initializeSchemaJoiner(schema);
        List<KGEntity> entityList = searchEntities(schema, entities);
        if (entityList.isEmpty()) {
            return joiner.toString();
        }
        List<KGRelationship> relationships = searchRelationships(schema, entityList);
        if (relationships.isEmpty()) {
            return joiner.toString();
        }
        KGGraph graph = graphMap.get(schema);
        for (KGRelationship relationship : relationships) {
            String source = relationship.getSource();
            KGEntity entity = graph.getEntityById(source);
            if (isDisease(entity)) {
                // If the source is a disease, we can add more symptoms
                String description = relationship.getDescription();
                joiner.add(description);
                continue;
            }
            String target = relationship.getTarget();
            entity = graph.getEntityById(target);
            if (isDisease(entity)) {
                // If the source is a disease, we can add more symptoms
                String description = relationship.getDescription();
                joiner.add(description);
            }
        }
        return joiner.toString();
    }


    private List<String> queryForDiseases(String schema, RagQuery query) {
        String input = query.getQuery();
        String[] entities = query.getEntities();
        List<KGEntity> entityList = searchEntities(schema, entities);
        if (entityList.isEmpty()) {
            return Collections.emptyList();
        }
        List<KGRelationship> relationships = searchRelationships(schema, entityList);
        if (relationships.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, DiseaseSymptoms> map = buildDiseaseSymptomsMap(schema, relationships);
        DiseaseSymptoms[] diseaseSymptoms = map.values().toArray(DiseaseSymptoms[]::new);
        Arrays.sort(diseaseSymptoms);
        List<String> diseaseNames = new ArrayList<>();
        for (int i = 0; i < Math.min(diseaseSymptoms.length, 3); i++) {
            diseaseNames.add(diseaseSymptoms[i].getDisease().getName());
        }
        return diseaseNames;
    }

    private String queryForHealthReport(String schema, RagQuery query, String[] diseases) {
        StringJoiner joiner = new StringJoiner("\n");
        String input = query.getQuery();
        String[] entities = query.getEntities();
        List<KGEntity> entityList = searchEntities(schema, entities);
        List<KGEntity> diseaseEntities = searchEntities(schema, diseases);
        if (!diseaseEntities.isEmpty()) {
            entityList.addAll(diseaseEntities);
        }
        if (entityList.isEmpty()) {
            return joiner.toString();
        }
        List<KGRelationship> relationships = searchRelationships(schema, entityList);
        if (relationships.isEmpty()) {
            return joiner.toString();
        }
        for (KGRelationship relationship : relationships) {
           joiner.add(relationship.getDescription());
        }
        return joiner.toString();
    }



    private boolean isDisease(KGEntity entity) {
        if (entity == null) {
            return false;
        }
        String type = entity.getType();
        return isDisease(type);
    }


    private String search(String schema, String input, String[] entities) {
        return searchComplete(schema, input, entities);
    }

    private String searchComplete(String schema, String input, String[] entities) {
        StringJoiner joiner = initializeSchemaJoiner(schema);

        addSegmentsToJoiner(schema, input, entities, joiner);
        addCommunitiesToJoiner(schema, input, entities, joiner);

        List<KGEntity> entityList = searchEntities(schema, entities);
        entityList = processEntitiesForSymptoms(schema, entities, entityList);

        if (!entityList.isEmpty()) {
            addRelationshipsToJoiner(schema, entityList, joiner);
        }

        return joiner.toString();
    }

    private StringJoiner initializeSchemaJoiner(String schema) {
        String schemaDescription = Schemas.getSchemaDescription(schema);
        return new StringJoiner("\n",
                "\n" + schemaDescription + ":\n" + String.format("<%s>\n", schemaDescription),
                String.format("\n</%s>\n", schemaDescription));
    }

    private void addSegmentsToJoiner(String schema, String input, String[] entities, StringJoiner joiner) {
        searchSegments(schema, input, entities).forEach(segment -> joiner.add(segment.getSegment()));
    }

    private void addCommunitiesToJoiner(String schema, String input, String[] entities, StringJoiner joiner) {
        searchCommunities(schema, input, entities).forEach(community -> joiner.add(community.getSummary()));
    }

    private List<KGEntity> processEntitiesForSymptoms(String schema, String[] entities, List<KGEntity> entityList) {
        boolean hasSymptom = false;
        List<KGEntity> nonSymptomList = new ArrayList<>();

        for (KGEntity kgEntity : entityList) {
            if (isSymptom(kgEntity.getType())) {
                hasSymptom = true;
            } else {
                nonSymptomList.add(kgEntity);
            }
        }

        if (!hasSymptom) {
            entityList = handleNoSymptoms(schema, entities, nonSymptomList);
        } else {
            entityList = handleSymptoms(schema, entityList, nonSymptomList);
        }

        return entityList;
    }

    private boolean isSymptom(String type) {
        return "症状".equals(type) || "临床表现".equals(type) || "病症".equals(type);
    }

    private List<KGEntity> handleNoSymptoms(String schema, String[] entities, List<KGEntity> nonSymptomList) {
        boolean hasSymptom = false;
        if (!schema.equals(Schemas.TCM) && !schema.equals(Schemas.EBM)) {
            KGGraph graph = graphMap.get(schema);
            List<KGEntity> entityList = new ArrayList<>();
            List<KGEntity> tcmEntityList = searchEntities(Schemas.TCM, entities);
            for (KGEntity kgEntity : tcmEntityList) {
                KGEntity localEntity = graph.getEntityById(kgEntity.getName());
                if (localEntity == null) {
                    continue;
                }
                entityList.add(localEntity);
                if (!isSymptom(kgEntity.getType())) {
                    nonSymptomList.add(kgEntity);
                } else {
                    hasSymptom = true;
                }
            }
            if (hasSymptom) {
                handleSymptoms(schema, entityList, nonSymptomList);
            }
        }
        return nonSymptomList;
    }

    private List<KGEntity> handleSymptoms(String schema, List<KGEntity> entityList, List<KGEntity> nonSymptomList) {
        KGGraph graph = graphMap.get(schema);
        Map<String, DiseaseSymptoms> map = new HashMap<>();

        List<KGRelationship> relationships = searchRelationships(schema, entityList);
        for (KGRelationship relationship : relationships) {
            processRelationship(graph, map, relationship, nonSymptomList);
        }

        return buildEntityListFromSymptoms(map, nonSymptomList, schema);
    }

    private void processRelationship(KGGraph graph, Map<String, DiseaseSymptoms> map, KGRelationship relationship, List<KGEntity> nonSymptomList) {
        String source = relationship.getSource();
        KGEntity entity = graph.getEntityById(source);

        if (entity == null) {
            return;
        }

        if (isDisease(entity.getType())) {
            map.computeIfAbsent(entity.getName(), r -> new DiseaseSymptoms(entity)).add(relationship);
        } else if (isSymptomRelationship(relationship.getRelation())) {
            nonSymptomList.add(entity);
        }
    }

    private Map<String, DiseaseSymptoms> buildDiseaseSymptomsMap(String schema, List<KGRelationship> relationships) {
        Map<String, DiseaseSymptoms> map = new HashMap<>();
        for (KGRelationship relationship : relationships) {
            String source = relationship.getSource();
            KGEntity entity = graphMap.get(schema).getEntityById(source);
            if (entity == null) {
                continue;
            }
            if (isDisease(entity.getType())) {
                map.computeIfAbsent(entity.getName(), r -> new DiseaseSymptoms(entity)).add(relationship);
            }
        }
        return map;
    }

    private boolean isDisease(String type) {
        return "病症".equals(type) || "疾病".equals(type);
    }

    private boolean isSymptomRelationship(String relationshipType) {
        switch (relationshipType) {
            case "表现":
            case "易发症状":
            case "常见表现":
            case "临床表现":
                return true;
            default:
                return false;
        }
    }

    private List<KGEntity> buildEntityListFromSymptoms(Map<String, DiseaseSymptoms> map, List<KGEntity> nonSymptomList, String schema) {
        DiseaseSymptoms[] diseaseSymptoms = map.values().toArray(DiseaseSymptoms[]::new);
        Arrays.sort(diseaseSymptoms);

        List<String> diseaseNames = new ArrayList<>();
        for (int i = 0; i < Math.min(diseaseSymptoms.length, MAX_COUNT); i++) {
            diseaseNames.add(diseaseSymptoms[i].getDisease().getName());
        }

        List<KGEntity> diseaseList = searchEntities(schema, diseaseNames.toArray(String[]::new));
        diseaseList.addAll(nonSymptomList);

        return diseaseList;
    }

    private void addRelationshipsToJoiner(String schema, List<KGEntity> entityList, StringJoiner joiner) {
        List<KGRelationship> relationships = searchRelationships(schema, entityList);
        for (KGRelationship relationship : relationships) {
            joiner.add(relationship.getDescription());
        }
    }

    private List<KGSegment> searchSegments(String schema, String input, String[] entities) {
        List<KGSegment> segments = jdbcRepository.semanticSearchForSegments(schema, input,
                DOC_SEGMENT_MAX_SIZE);
        return segments;
    }
    private List<KGCommunity> searchCommunities(String schema, String input, String[] entities) {
        List<KGCommunity> communities = jdbcRepository.semanticSearchForCommunities(
                schema, input, DOC_SEGMENT_MAX_SIZE);
        return communities;
    }

    private List<KGEntity> searchEntities(String schema, String[] entities) {
        List<KGEntity> entityList = jdbcRepository.getEntities(schema, entities, 6);
        int maxSize = getMaxEntitySize(schema);
        if (entityList.size() > maxSize) {
            return entityList.subList(0, maxSize);
        }
        return entityList;
    }

    private List<KGRelationship> searchRelationships(String schema,  List<KGEntity> entityList) {
        String[] entities = new String[entityList.size()];
        for (int i = 0; i < entityList.size(); i++) {
            entities[i] = entityList.get(i).getName();
        }
        KGGraph graph = graphMap.get(schema);
        List<KGRelationship> relationships = new ArrayList<>();
        for (String entity : entities) {
            List<KGRelationship> list = graph.getRelationships(entity);
            if (list != null && !list.isEmpty()) {
                relationships.addAll(list);
            }
        }
        int maxSize = getMaxRelationshipSize(schema);
        if (relationships.size() > maxSize) {
            return relationships.subList(0, maxSize);
        }
        return relationships;
    }

    private String buildSegmentsContent(List<KGSegment> segments) {
        StringJoiner joiner = new StringJoiner("\n");
        for (KGSegment segment : segments) {
            joiner.add(segment.getSegment());
        }
        return joiner.toString();
    }

    private String buildCommunitiesContent(List<KGCommunity> communities) {
        StringJoiner joiner = new StringJoiner("\n");
        for (KGCommunity community : communities) {
            joiner.add(community.getSummary());
        }
        return joiner.toString();
    }

    private String buildEntitiesContent(List<KGEntity> entities) {
        StringJoiner joiner = new StringJoiner("\n");
        for (KGEntity entity : entities) {
            joiner.add(entity.toString());
        }
        return joiner.toString();
    }

    private String buildRelationshipsContent(List<KGRelationship> relationships) {
        StringJoiner joiner = new StringJoiner("\n");
        for (KGRelationship relationship : relationships) {
            joiner.add(relationship.toString());
        }
        return joiner.toString();
    }


    class DiseaseSymptoms implements Comparable<DiseaseSymptoms> {
        private KGEntity disease;
        private List<KGRelationship> symptoms;

        public DiseaseSymptoms(KGEntity disease) {
            this.disease = disease;
            this.symptoms = new ArrayList<>();
        }

        public void add(KGRelationship symptom) {
            symptoms.add(symptom);
        }

        public KGEntity getDisease() {
            return disease;
        }

        public List<KGRelationship> getSymptoms() {
            return symptoms;
        }

        @Override
        public int compareTo(DiseaseSymptoms o) {
            int code = Integer.compare(o.symptoms.size(), symptoms.size());
            return code;
        }
    }

    public JdbcRepository getJdbcRepository() {
        return jdbcRepository;
    }
}
