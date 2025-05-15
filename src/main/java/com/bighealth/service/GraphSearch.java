package com.bighealth.service;

import com.bighealth.entity.*;
import com.bighealth.llm.LLMModel;
import com.bighealth.llm.RagQuery;
import com.bighealth.repository.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GraphSearch {
    public static final int MAX_COUNT = 32;

    @Autowired
    private JdbcRepository jdbcRepository;

    @Autowired
    private LLMModel llmModel;

    public static final int RELATIONSHIP_MAX_SIZE = 80;
    public static final int DOC_SEGMENT_MAX_SIZE = 8;

    public static final int ENTITY_MAX_SIZE = 40;

    private Map<String, KGGraph> graphMap = new HashMap<>();

    public GraphSearch() {

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
                "\n<健康数据>\n", "\n</健康数据>\n");
        String input = query.getQuery();
        String[] entities = query.getEntities();
        for (String schema : Schemas.SCHEMAS) {
            String result = search(schema, input, entities);
            joiner.add(result);
        }
        /*
        Arrays.stream(Schemas.SCHEMAS)
                .parallel()
                .map(schema -> search(schema, input, entities))
                .filter(result -> result != null && !result.isEmpty())
                .forEach(joiner::add);

         */
        return joiner.toString();
    }


    private String search(String schema, String input, String[] entities) {
        return searchComplete(schema, input, entities);
    }

    private String searchComplete(String schema, String input, String[] entities) {
        String schemaDescription = Schemas.getSchemaDescription(schema);
        StringJoiner joiner = initializeSchemaJoiner(schemaDescription);

        addSegmentsToJoiner(schema, input, entities, joiner);
        addCommunitiesToJoiner(schema, input, entities, joiner);

        List<KGEntity> entityList = searchEntities(schema, entities);
        entityList = processEntitiesForSymptoms(schema, entities, entityList);

        if (!entityList.isEmpty()) {
            addRelationshipsToJoiner(schema, entityList, joiner);
        }

        return joiner.toString();
    }

    private StringJoiner initializeSchemaJoiner(String schemaDescription) {
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
