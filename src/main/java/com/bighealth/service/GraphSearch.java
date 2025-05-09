package com.bighealth.service;

import com.bighealth.entity.KGCommunity;
import com.bighealth.entity.KGEntity;
import com.bighealth.entity.KGRelationship;
import com.bighealth.entity.KGSegment;
import com.bighealth.llm.LLMModel;
import com.bighealth.llm.RagQuery;
import com.bighealth.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GraphSearch {
    public static final int MAX_COUNT = 4096;

    @Autowired
    private KGEntityRepository entityRepository;

    @Autowired
    private KGSegmentRepository segmentRepository;

    @Autowired
    private KGRelationshipRepository relationshipRepository;

    @Autowired
    private KGCommunityRepository communityRepository;

    @Autowired
    private JdbcRepository jdbcRepository;

    @Autowired
    private LLMModel llmModel;

    public static final int RELATIONSHIP_MAX_SIZE = 100;
    public static final int DOC_SEGMENT_MAX_SIZE = 8;

    public static final int ENTITY_MAX_SIZE = 32;


    public String search(RagQuery query) {
        StringJoiner joiner = new StringJoiner("\n");
        String input = query.getQuery();
        String[] entities = query.getEntities();
        for (String schema : Schemas.SCHEMAS) {
            String result = search(schema, input, entities);
            if (result != null && !result.isEmpty()) {
                joiner.add(result);
            }
        }
        return joiner.toString();
    }


    private String search(String schema, String input, String[] entities) {
       /* switch (schema) {
            case Schemas.TCM:
                return searchComplete(schema, input, entities);
            default:
                return searchSchema(schema, input, entities);
        }*/
        return searchComplete(schema, input, entities);
    }

    private String searchSchema(String schema, String input, String[] entities) {
        String schemaDescription = Schemas.getSchemaDescription(schema);
        StringJoiner joiner = new StringJoiner("\n", String.format("<%s>", schemaDescription),
                String.format("</%s>", schemaDescription));
        searchSegments(schema, input, entities).forEach(segment -> {
            joiner.add(segment.getSegment());
        });
        searchCommunities(schema, input, entities).forEach(community -> {
            joiner.add(community.getSummary());
        });
        List<KGEntity> entityList = searchEntities(schema, entities);
        if (entityList.isEmpty()) {
            return joiner.toString();
        }
        List<KGRelationship> relationships = searchRelationships(schema, entityList);
        for (KGRelationship relationship : relationships) {
            joiner.add(relationship.getDescription());
        }
        return joiner.toString();
    }

    private String searchComplete(String schema, String input, String[] entities) {
        String schemaDescription = Schemas.getSchemaDescription(schema);
        StringJoiner joiner = new StringJoiner("\n", String.format("<%s>", schemaDescription),
                String.format("</%s>", schemaDescription));
        searchSegments(Schemas.TCM, input, entities).forEach(segment -> {
            joiner.add(segment.getSegment());
        });
        searchCommunities(Schemas.TCM, input, entities).forEach(community -> {
            joiner.add(community.getSummary());
        });
        List<KGEntity> entityList = searchEntities(Schemas.TCM, entities);
        boolean hasSymptom = false;
        List<KGEntity> nonSymptomList = new ArrayList<>();
        for (KGEntity kgEntity : entityList) {
            String type = kgEntity.getType();
            switch (type) {
                case "症状":
                case "临床表现":
                case "病症":
                    hasSymptom = true;
                    break;
                default:
                    nonSymptomList.add(kgEntity);
            }
        }

        if (hasSymptom) {
            Map<String, DiseaseSymptoms> map = new HashMap<>();
            // get the top 3
            // try to search all the symptoms for diseases, symptom is target, therefore, we
            // set source to false and filter duplicate

            List<KGRelationship> relationships = searchRelationships(Schemas.TCM, entityList);
            for (KGRelationship relationship : relationships) {
                String source = relationship.getSource();
                KGEntity entity = entityRepository.findById(source).orElse(null);
                if (entity == null) {
                    continue;
                }
                String type = entity.getType();
                switch (type) {
                    case "病症":
                    case "疾病":
                        String name = entity.getName();
                        DiseaseSymptoms d = map.computeIfAbsent(name, r -> {
                            return new DiseaseSymptoms(entity);
                        });
                        d.add(relationship);
                        break;
                    default:
                        String relationshipType = relationship.getRelation();
                        switch (relationshipType) {
                            case "表现":
                            case "易发症状":
                            case "常见表现":
                            case "临床表现":
                                nonSymptomList.add(entity);
                        }
                }
            }
            DiseaseSymptoms[] diseaseSymptoms = map.values().toArray(DiseaseSymptoms[]::new);
            Arrays.sort(diseaseSymptoms);
            int count = 0;
            List<String> diseaseNames = new ArrayList<>();
            for (DiseaseSymptoms d : diseaseSymptoms) {
                diseaseNames.add(d.disease.getName());
                count++;
                if (count >= MAX_COUNT) {
                    break;
                }
            }
            List<KGEntity> diseaseList = searchEntities(Schemas.TCM, diseaseNames.toArray(String[]::new));
            diseaseList.addAll(nonSymptomList);
            entityList = diseaseList;
        }
        if (entityList.isEmpty()) {
            return joiner.toString();
        }
        List<KGRelationship> relationships = searchRelationships(Schemas.TCM, entityList);
        for (KGRelationship relationship : relationships) {
            joiner.add(relationship.getDescription());
        }
        return joiner.toString();
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
        return entityList;
    }

    private List<KGRelationship> searchRelationships(String schema,  List<KGEntity> entityList) {
        String[] entities = new String[entityList.size()];
        for (int i = 0; i < entityList.size(); i++) {
            entities[i] = entityList.get(i).getName();
        }
        List<KGRelationship> relationships = jdbcRepository.getRelationships(schema, entities);
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


}
