package com.bighealth.llm;

import dev.langchain4j.memory.ChatMemory;

import java.util.Map;

import static com.bighealth.controller.StreamingChatController.RAG_QUERY;

public class SessionData {
    private Map<String, Object> userData;
    private ChatMemory chatMemory;

    public SessionData() {
    }

    public RagQuery getRagQuery() {
        return (RagQuery) userData.get(RAG_QUERY);

    }

    public void setRagQuery(RagQuery ragQuery) {
        userData.put(RAG_QUERY, ragQuery);
    }

    public void setUserData(Map<String, Object> userData) {
        this.userData = userData;
    }

    public ChatMemory getChatMemory() {
        return chatMemory;
    }

    public void setChatMemory(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }
}
