package com.bighealth.llm;

import com.bighealth.service.GraphSearch;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.util.StringTokenizer;

public class MeasureTools {
    private GraphSearch graphSearch;

    private SessionData sessionData;

    public MeasureTools(SessionData sessionData, GraphSearch graphSearch) {
        this.sessionData = sessionData;
        this.graphSearch = graphSearch;
    }


    @Tool("""
          Use this tool to query back end system with a text as query parameter.  
            """)
    public String query(@P("input") String arg0) {
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
        sessionData.setRagQuery(ragQuery);
        String result = graphSearch.primarySearch(ragQuery);
        return result;
    }
}
