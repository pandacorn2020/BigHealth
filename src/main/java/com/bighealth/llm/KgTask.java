package com.bighealth.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;

import java.util.concurrent.Callable;

public class KgTask implements Callable<String> {
    private ChatMessage systemMessage;
    private String ragKgSystemPrompt;

    private String ragKgUserPrompt;

    private TextSegment segment;

    private String text;

    private ChatLanguageModel model;

    public KgTask(ChatMessage systemMessage,
                  String ragKgSystemPrompt,
                  String ragKgUserPrompt,
                  TextSegment segment,
                  ChatLanguageModel model) {
        this.systemMessage = systemMessage;
        this.segment = segment;
        this.ragKgSystemPrompt = ragKgSystemPrompt;
        this.ragKgUserPrompt = ragKgUserPrompt;
        this.model = model;
        if (segment.text().length() >= 8000) {
            System.out.println("break;");
        }
    }

    public String getText() {
        return text;
    }

    @Override
    public String call() {
        try {
            ChatMessage systemMessage = SystemMessage.systemMessage(ragKgSystemPrompt);
            String message = String.format(ragKgUserPrompt, segment.text());
            ChatMessage userMessage = UserMessage.userMessage(message);
            Response<AiMessage> response = model.generate(systemMessage, userMessage);
            this.text = response.content().text();
            return text;
        } catch (Throwable t) {
            return null;
        }
    }
}