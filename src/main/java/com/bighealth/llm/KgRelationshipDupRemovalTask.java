package com.bighealth.llm;

import com.bighealth.entity.KGRelationship;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;

import java.util.concurrent.Callable;

public class KgRelationshipDupRemovalTask implements Callable<KGRelationship> {
    private String dupRemovalTemplate;

    private KGRelationship relationship;

    private ChatLanguageModel model;

    public KgRelationshipDupRemovalTask(String dupRemovalTemplate,
                                        KGRelationship relationship,
                                  ChatLanguageModel model) {
        this.dupRemovalTemplate = dupRemovalTemplate;
        this.relationship = relationship;
        this.model = model;
    }

    @Override
    public KGRelationship call() {
        try {
            String message = String.format(dupRemovalTemplate,
                    relationship.getDescription());
            ChatMessage userMessage = UserMessage.userMessage(message);
            Response<AiMessage> response = model.generate(userMessage);
            String text = response.content().text();
            relationship.setDescription(text);
            return relationship;
        } catch (Throwable t) {
            return null;
        }
    }
}