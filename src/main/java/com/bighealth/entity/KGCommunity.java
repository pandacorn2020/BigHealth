package com.bighealth.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.util.StringJoiner;

@Entity
@Data
public class KGCommunity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String summary;

    public KGCommunity() {
    }

    public KGCommunity(String name, String summary) {
        this.name = name;
        this.summary = summary;
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(",");
        joiner.add(name);
        joiner.add(summary);
        return joiner.toString();
    }
}
