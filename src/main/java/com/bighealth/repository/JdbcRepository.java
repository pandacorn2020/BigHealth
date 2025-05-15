package com.bighealth.repository;

import com.bighealth.entity.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Repository
@Transactional
public class JdbcRepository {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // create a RowMapper for KGEntity
    private RowMapper<KGEntity> kgEntityRowMapper = new RowMapper<KGEntity>() {
        @Override
        public KGEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            float score = rs.getFloat(SCORE_COLUMN);
            if (score < KG_COMMINITY_SCORE) {
                return null;
            }
            KGEntity kgEntity = new KGEntity();
            kgEntity.setName(rs.getString("name"));
            kgEntity.setType(rs.getString("type"));
            kgEntity.setDescription(rs.getString("description"));
            if(kgEntity.getName() == null || kgEntity.getType() == null) {
                return null;
            }
            return kgEntity;
        }
    };

    private RowMapper<KGEntity> noScoreKgEntityRowMapper = new RowMapper<KGEntity>() {
        @Override
        public KGEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            KGEntity kgEntity = new KGEntity();
            kgEntity.setName(rs.getString("name"));
            kgEntity.setType(rs.getString("type"));
            kgEntity.setDescription(rs.getString("description"));
            return kgEntity;
        }
    };

    private RowMapper<KGRelationship> kgRelationshipRowMapper = new RowMapper<KGRelationship>() {
        @Override
        public KGRelationship mapRow(ResultSet rs, int rowNum) throws SQLException {
            KGRelationship kgRelationship = new KGRelationship();
            String source = rs.getString("source");
            String target = rs.getString("target");
            String relation = rs.getString("relation");
            KGRelationshipKey key = new KGRelationshipKey(source, target, relation);
            kgRelationship.setId(key);
            kgRelationship.setDescription(rs.getString("description"));
            return kgRelationship;
        }
    };


    private RowMapper<KGSegment> kgSegmentRowMapper = new RowMapper<KGSegment>() {
        @Override
        public KGSegment mapRow(ResultSet rs, int rowNum) throws SQLException {
            float score = rs.getFloat(SCORE_COLUMN);
            if (score < DOC_SEGMENT_SCORE) {
                return null;
            }
            KGSegment kgSegment = new KGSegment();
            kgSegment.setId(rs.getLong("id"));
            kgSegment.setSegment(rs.getString("segment"));
            return kgSegment;
        }
    };

    private RowMapper<KGSegment> noScoreKgSegmentRowMapper = new RowMapper<KGSegment>() {
        @Override
        public KGSegment mapRow(ResultSet rs, int rowNum) throws SQLException {
            KGSegment kgSegment = new KGSegment();
            kgSegment.setId(rs.getLong("id"));
            kgSegment.setSegment(rs.getString("segment"));
            return kgSegment;
        }
    };


    private RowMapper<KGCommunity> kgCommunityRowMapper = new RowMapper<KGCommunity>() {
        @Override
        public KGCommunity mapRow(ResultSet rs, int rowNum) throws SQLException {
            float score = rs.getFloat(SCORE_COLUMN);
            if (score < KG_COMMINITY_SCORE) {
                return null;
            }
            KGCommunity kgCommunity = new KGCommunity();
            kgCommunity.setName(rs.getString("name"));
            kgCommunity.setSummary(rs.getString("summary"));
            return kgCommunity;
        }
    };

    private RowMapper<KGCommunity> noScoreKgCommunityRowMapper = new RowMapper<KGCommunity>() {
        @Override
        public KGCommunity mapRow(ResultSet rs, int rowNum) throws SQLException {
            KGCommunity kgCommunity = new KGCommunity();
            kgCommunity.setName(rs.getString("name"));
            kgCommunity.setSummary(rs.getString("summary"));
            return kgCommunity;
        }
    };

    // Method to perform semantic search for entities
    public List<KGEntity> semanticSearchForEntities(String schema, String e, int topCount) {
        String sql = "SELECT * FROM %s.kgentity WHERE name vsearch ? top %d";
        sql = String.format(sql, schema, topCount);
        List<KGEntity> entities = jdbcTemplate.query(sql, kgEntityRowMapper, e);
        return entities.stream()
                .filter(entity -> entity != null)
                .collect(Collectors.toList());
    }

    public List<KGEntity> getAllEntities(String schema) {
        String sql = "SELECT * FROM %s.kgentity";
        sql = String.format(sql, schema);
        List<KGEntity> entities = jdbcTemplate.query(sql, noScoreKgEntityRowMapper);
        return entities.stream()
                .filter(entity -> entity != null)
                .collect(Collectors.toList());
    }

    // Method to get entities
    public List<KGEntity> getEntities(String schema, String[] es, int topCount) {
        List<KGEntity> entities = new ArrayList<>();
        for (String e : es) {
            entities.addAll(semanticSearchForEntities(schema, e, topCount));
        }
        return entities;
    }

    // Method to get relationships
    public List<KGRelationship> getAllRelationships(String schema) {
        String sql = "SELECT * FROM %s.kgrelationship";
        sql = String.format(sql, schema);
        List<KGRelationship> relationships = jdbcTemplate.query(sql, kgRelationshipRowMapper);
        return relationships.stream()
                .filter(relationship -> relationship != null)
                .collect(Collectors.toList());
    }

    // Method to perform semantic search for segments
    public List<KGSegment> semanticSearchForSegments(String schema, String input, int topCount) {
        String sql = "SELECT * FROM %s.kgsegment WHERE segment vsearch ? top %d";
        sql = String.format(sql, schema, topCount);
        List<KGSegment> segments = jdbcTemplate.query(sql, kgSegmentRowMapper, input);
        return segments.stream()
                .filter(segment -> segment != null)
                .collect(Collectors.toList());
    }

    public List<KGSegment> getAllSegments(String schema) {
        String sql = "SELECT * FROM %s.kgsegment";
        sql = String.format(sql, schema);
        List<KGSegment> segments = jdbcTemplate.query(sql, noScoreKgSegmentRowMapper);
        return segments.stream()
                .filter(segment -> segment != null)
                .collect(Collectors.toList());
    }

    // Method to perform semantic search for communities
    public List<KGCommunity> semanticSearchForCommunities(String schema, String input, int topCount) {
        String sql = "SELECT * FROM %s.kgcommunity WHERE summary vsearch ? top %d";
        sql = String.format(sql, schema, topCount);
        List<KGCommunity> communities = jdbcTemplate.query(sql, kgCommunityRowMapper, input);
        return communities.stream()
                .filter(community -> community != null)
                .collect(Collectors.toList());
    }

    public List<KGCommunity> getAllCommunities(String schema) {
        String sql = "SELECT * FROM %s.kgcommunity";
        sql = String.format(sql, schema);
        List<KGCommunity> communities = jdbcTemplate.query(sql, noScoreKgCommunityRowMapper);
        return communities.stream()
                .filter(community -> community != null)
                .collect(Collectors.toList());
    }

    public KGRelationship getRelationship(String schema, String source, String target, String relation) {
        String sql = "SELECT * FROM %s.kgrelationship WHERE source = %s AND target = %s AND relation = %s";
        sql = String.format(sql, schema, source, target, relation);
        List<KGRelationship> list = jdbcTemplate.query(sql, kgRelationshipRowMapper);
        if (list.size() > 0) {
            return list.get(0);
        }
        return null;
    }

    public void saveKGFile(String schema, String name) {
        String sql = String.format("INSERT INTO %s.kgfile (name) VALUES (?)", schema);
        jdbcTemplate.update(sql, name);
    }

    // Method to save KGEntity
    public void saveKGEntity(String schema, KGEntity entity) {
        String sql = String.format("INSERT INTO %s.kgentity (name, type, description) VALUES (?, ?, ?)", schema);
        jdbcTemplate.update(sql, entity.getName(), entity.getType(), entity.getDescription());
    }

    // Method to save KGRelationship
    public void saveKGRelationship(String schema, KGRelationship relationship) {
        String sql = String.format("INSERT INTO %s.kgrelationship (source, target, relation, description) VALUES (?, ?, ?, ?)", schema);
        jdbcTemplate.update(sql, relationship.getSource(), relationship.getTarget(), relationship.getRelation(), relationship.getDescription());
    }

    // Method to save KGSegment
    public void saveKGSegment(String schema, KGSegment segment) {
        String sql = String.format("INSERT INTO %s.kgsegment (segment) VALUES (?)", schema);
        jdbcTemplate.update(sql, segment.getSegment());
    }

    // Method to save KGCommunity
    public void saveKGCommunity(String schema, KGCommunity community) {
        String sql = String.format("INSERT INTO %s.kgcommunity (name, summary) VALUES (?, ?)", schema);
        jdbcTemplate.update(sql, community.getName(), community.getSummary());
    }

    // Method to save multiple KGEntity
    public void saveAllKGEntities(String schema, List<KGEntity> entities) {
        String sql = String.format("INSERT INTO %s.kgentity (name, type, description) VALUES (?, ?, ?)", schema);
        List<Object[]> batchArgs = new ArrayList<>();
        for (KGEntity entity : entities) {
            batchArgs.add(new Object[]{entity.getName(), entity.getType(), entity.getDescription()});
        }
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

    // Method to save multiple KGRelationship
    public void saveAllKGRelationships(String schema, List<KGRelationship> relationships) {
        String sql = String.format("INSERT INTO %s.kgrelationship (source, target, relation, description) VALUES (?, ?, ?, ?)", schema);
        List<Object[]> batchArgs = new ArrayList<>();
        for (KGRelationship relationship : relationships) {
            batchArgs.add(new Object[]{relationship.getSource(), relationship.getTarget(), relationship.getRelation(), relationship.getDescription()});
        }
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

    // Method to save multiple KGSegment
    public void saveAllKGSegments(String schema, List<KGSegment> segments) {
        String sql = String.format("INSERT INTO %s.kgsegment (segment) VALUES (?)", schema);
        List<Object[]> batchArgs = new ArrayList<>();
        for (KGSegment segment : segments) {
            batchArgs.add(new Object[]{segment.getSegment()});
        }
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }

    // Method to save multiple KGCommunity
    public void saveAllKGCommunities(String schema, List<KGCommunity> communities) {
        String sql = String.format("INSERT INTO %s.kgcommunity (name, summary) VALUES (?, ?)", schema);
        List<Object[]> batchArgs = new ArrayList<>();
        for (KGCommunity community : communities) {
            batchArgs.add(new Object[]{community.getName(), community.getSummary()});
        }
        jdbcTemplate.batchUpdate(sql, batchArgs);
    }
    // Method to query KGEntity by ID
    public String findKGFileByName(String schema, String name) {
        try {
            String sql = String.format("SELECT name FROM %s.kgfile WHERE name = ?", schema);
            return jdbcTemplate.queryForObject(sql, String.class, name);
        } catch (Exception e) {
            return null;
        }
    }

    public KGEntity findKGEntityById(String schema, String id) {
        try {
            String sql = String.format("SELECT * FROM %s.kgentity WHERE name = ?", schema);
            return jdbcTemplate.queryForObject(sql, noScoreKgEntityRowMapper, id);
        } catch (Exception e) {
            return null;
        }
    }

    public void deleteKGEntityById(String schema, String id) {
        try {
            String sql = String.format("DELETE FROM %s.kgentity WHERE name = ?", schema);
            jdbcTemplate.update(sql, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete KGEntity");
        }
    }

    // Method to query KGRelationship by ID
    public KGRelationship findKGRelationshipById(String schema, KGRelationshipKey id) {
        try {
            String sql = String.format("SELECT * FROM %s.kgrelationship WHERE source = ? AND target = ? AND relation = ?",
                    schema);
            return jdbcTemplate.queryForObject(sql, kgRelationshipRowMapper, id.getSource(), id.getTarget(), id.getRelation());
        } catch (Exception e) {
            return null;
        }
    }

    public void deleteKGRelationshipById(String schema, KGRelationshipKey id) {
        try {
            String sql = String.format("delete FROM %s.kgrelationship WHERE source = ? AND target = ? AND relation = ?", schema);
            jdbcTemplate.update(sql, id.getSource(), id.getTarget(), id.getRelation());
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete KGRelationship");
        }
    }

    public static final String SCORE_COLUMN = "__SCORE__";
    public static final float ENTITY_SCORE = 0.949f;

    public static final float DOC_SEGMENT_SCORE = 0.75f;

    public static final float KG_COMMINITY_SCORE = 0.75f;

    public static final float KG_RELATION_SHIP_SCORE = 0.50f;
}