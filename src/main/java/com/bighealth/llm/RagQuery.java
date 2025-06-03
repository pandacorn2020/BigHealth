package com.bighealth.llm;


import java.util.StringTokenizer;

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

    public static RagQuery valueOf(String arg0) {
        String inputText = arg0;
        String[] entities = null;
        if (arg0.contains("|")) {
            int index = arg0.indexOf("|");
            inputText = arg0.substring(0, index);
            String s1 = arg0.substring(index + 1);
            StringTokenizer stz = new StringTokenizer(s1, ",");
            int count = stz.countTokens();
            entities = new String[count];
            for (int i = 0; i < count; i++) {
                entities[i] = stz.nextToken();
            }
        }
        RagQuery ragQuery = new RagQuery();
        ragQuery.setQuery(inputText);
        ragQuery.setEntities(entities);
        return ragQuery;
    }
}