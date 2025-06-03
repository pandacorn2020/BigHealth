package com.bighealth.llm;

import com.bighealth.service.GraphSearch;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

public class MeasureTools {
    private GraphSearch graphSearch;

    private SessionData sessionData;
    private static Logger logger = org.apache.logging.log4j.LogManager.getLogger(MeasureTools.class);

    public MeasureTools(SessionData sessionData, GraphSearch graphSearch) {
        this.sessionData = sessionData;
        this.graphSearch = graphSearch;
    }


    @Tool("""
          Use this tool to query for more symptoms with a text as query parameter.  
            """)
    public String queryForMoreSymptoms(@P("input") String arg0) {
        logger.info("MeasureTools.queryForMoreSymptoms: {}", arg0);
        String inputText = arg0;
        RagQuery ragQuery = RagQuery.valueOf(arg0);
        String result = graphSearch.queryForMoreSymptoms(ragQuery);
        logger.info("result: {}", result);
        return result;
    }

    @Tool("""
          Use this tool to query for health report with a text as query parameter.  
            """)
    public String queryForHealthReport(@P("input") String arg0) {
        logger.info("MeasureTools.queryForHealthReport: {}", arg0);
        RagQuery ragQuery = RagQuery.valueOf(arg0);
        sessionData.setRagQuery(ragQuery);
        logger.info("RagQuery: {}", ragQuery);
        String result = graphSearch.queryForHealthReport(ragQuery);
        logger.info("result: {}", result);
        return result;
    }

    @Tool("""
          Use this tool to get associated factors report health data, there is no input parameter needed.  
            """)
    public String getAssociatedFactorsData() {
        RagQuery ragQuery = sessionData.getRagQuery();
        if (ragQuery == null) {
            return "No associated factors report available. Please provide a valid query.";
        }
        logger.info("RagQuery: {}", ragQuery);
        String result = graphSearch.queryForAssociatedFactorsReport(ragQuery);
        logger.info("result: {}", result);
        return result;
    }

    @Tool("""
          Use this tool to query for drug info with a text as query parameter.  
            """)
    public String queryForDrugInfo(@P("input") String arg0) {
        logger.info("MeasureTools.queryForDrugInfo: {}", arg0);
        String inputText = arg0;
        RagQuery ragQuery = RagQuery.valueOf(arg0);
        logger.info("RagQuery: {}", inputText, ragQuery);
        String result = graphSearch.queryForDrugInfo(ragQuery);
        logger.info("result: {}", result);
        return result;
    }

    @Tool("""
          Use this tool to query for general health info with a text as query parameter.  
            """)
    public String queryGeneralHealthInfo(@P("input") String arg0) {
        logger.info("MeasureTools.queryForGeneralHealthInfo: {}", arg0);
        RagQuery ragQuery = RagQuery.valueOf(arg0);
        logger.info("RagQuery: {}", ragQuery);
        String result = graphSearch.queryForGeneralHealthInfo(ragQuery);
        logger.info("result: {}", result);
        return result;
    }

}
