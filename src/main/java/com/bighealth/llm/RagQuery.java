package com.bighealth.llm;


public class RagQuery {

    private String inputType;
    private String query;

    private String[] entities;

    public String getInputType() {
        return inputType;
    }

    public void setInputType(String inputType) {
        this.inputType = inputType;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public void setEntities(String[] entities) {
        this.entities = entities;
    }

    public String getQuery() {
        return query;
    }

    public String[] getEntities() {
        return entities;
    }

    public String toString() {
        return "query=" + query + ", entities=" + String.join(", ", entities) +
                ", inputType=" + inputType;
    }
}