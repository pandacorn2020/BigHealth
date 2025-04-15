package com.bighealth.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class KGSegment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String segment;

    public KGSegment() {
    }

    public KGSegment(String segment) {
        this.segment = segment;
    }

    @Override
    public String toString() {
        return segment;
    }
}
