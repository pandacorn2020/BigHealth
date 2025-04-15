package com.bighealth.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.StringJoiner;

@Entity
@Data
public class KGRelationship {

    @EmbeddedId
    private KGRelationshipKey id;

    private String description;

    @Transient
    private boolean merged;

    @Override
    public String toString() {
        return description;
    }

    public KGRelationship() {
    }
// getSource, getTarget, getRelation
    public KGRelationshipKey getId() {
        return id;
    }

    public String getSource() {
        return id.getSource();
    }
    public String getTarget() {
        return id.getTarget();
    }
    public String getRelation() {
        return id.getRelation();
    }
    public KGRelationship(String source, String target, String relation, String description) {
        this.id = new KGRelationshipKey(source, target, relation);
        this.description = description;
    }

    public void merge(KGRelationship relationship) {
        if (relationship == null) {
            return;
        }
        // add key comparison, if not equal, do not merge
        if (!id.equals(relationship.getId())) {
            throw new RuntimeException("Cannot merge relationships with different keys");
        }
        StringJoiner joiner = new StringJoiner("\n");
        joiner.add(description);
        joiner.add(relationship.description);
        description = joiner.toString();
        this.merged = true;
    }


    public boolean isMerged() {
        return merged;
    }

    public void setMerged(boolean merged) {
        this.merged = merged;
    }
}