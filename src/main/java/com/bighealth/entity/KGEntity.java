package com.bighealth.entity;

import com.bighealth.util.TextSimilarity;
import jakarta.persistence.*;
import lombok.Data;

import java.util.Objects;
import java.util.StringJoiner;

@Entity
@Data
public class KGEntity {
    // name, type, description
    @Id
    private String name;
    private String type;
    private String description;

    @Transient
    private boolean merged;

    public KGEntity() {
    }

    public KGEntity(String name, String type, String description) {
        this.name = name;
        this.type = type;
        this.description = description;
    }

    public void merge(KGEntity entity) {
        if (entity == null) {
            return;
        }
        if (!name.equals(entity.getName())) {
            throw new RuntimeException("Cannot merge entities with different names");
        }
        StringJoiner joiner = new StringJoiner(" ");
        joiner.add(description);
        joiner.add(entity.description);
        description = joiner.toString();
        this.merged = true;
    }

    private double score(KGEntity entity) {
        return TextSimilarity.getSimilarity(description, entity.description);
    }

    public boolean isMerged() {
        return merged;
    }

    public void setMerged(boolean merged) {
        this.merged = merged;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KGEntity kgEntity = (KGEntity) o;
        return Objects.equals(name, kgEntity.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(",");
        joiner.add(name);
        joiner.add(type);
        joiner.add(description);
        return joiner.toString();
    }
}
