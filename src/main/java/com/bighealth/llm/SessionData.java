package com.bighealth.llm;

import dev.langchain4j.memory.ChatMemory;

public class SessionData {
    private RagQuery ragQuery;
    private ChatMemory chatMemory;

    public SessionData() {
    }

    public RagQuery getRagQuery() {
        return ragQuery;
    }

    public void setRagQuery(RagQuery ragQuery) {
        this.ragQuery = ragQuery;
    }

    public ChatMemory getChatMemory() {
        return chatMemory;
    }

    public void setChatMemory(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }
}
