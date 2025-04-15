package com.bighealth.llm;

import com.bighealth.entity.KGEntity;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;

import java.util.concurrent.Callable;
import java.util.logging.Logger;

public class KgEntityDupRemovalTask implements Callable<KGEntity> {
    private String dupRemovalTemplate;

    private KGEntity entity;

    private ChatLanguageModel model;

    private Logger logger = Logger.getLogger(KgEntityDupRemovalTask.class.getSimpleName());

    public KgEntityDupRemovalTask(String dupRemovalTemplate,
                                  KGEntity entity,
                                  ChatLanguageModel model) {
        this.dupRemovalTemplate = dupRemovalTemplate;
        this.entity = entity;
        this.model = model;
    }

    @Override
    public KGEntity call() {
        try {
            String message = String.format(dupRemovalTemplate, entity.getDescription());
            ChatMessage userMessage = UserMessage.userMessage(message);
            Response<AiMessage> response = model.generate(userMessage);
            String text = response.content().text();
            logger.log(java.util.logging.Level.INFO, "Dup removal response: " + text + ", original text: " + entity.getDescription());
            entity.setDescription(text);
            return entity;
        } catch (Throwable t) {
            return null;
        }
    }
}