package com.bighealth.entity;

import com.fasterxml.jackson.databind.ObjectMapper;

public class CommunityData {
    private String name;
    private String summary;

    public CommunityData() {
    }

    public CommunityData(String name, String summary) {
        this.name = name;
        this.summary = summary;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public static CommunityData read(String jsonText) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(jsonText, CommunityData.class);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
