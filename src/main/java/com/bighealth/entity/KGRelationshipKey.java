package com.bighealth.entity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.StringJoiner;

@Embeddable
public class KGRelationshipKey implements Serializable {

    private String source;
    private String target;
    private String relation;

    public KGRelationshipKey() {
    }

    public KGRelationshipKey(String source, String target, String relation) {
        this.source = source;
        this.target = target;
        this.relation = relation;
    }

    // Getters and Setters

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        joiner.add(source);
        joiner.add(target);
        joiner.add(relation);
        return joiner.toString();
    }

    public String getSource() {
        return source;
    }

    public String getTarget() {
        return target;
    }

    public String getRelation() {
        return relation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KGRelationshipKey that = (KGRelationshipKey) o;
        return Objects.equals(source, that.source) &&
                Objects.equals(target, that.target) &&
                Objects.equals(relation, that.relation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, target, relation);
    }
}