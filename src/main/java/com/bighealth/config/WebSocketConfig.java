package com.bighealth.config;

import com.bighealth.llm.CustomWebSocketHandler;
import com.bighealth.llm.LLMModel;
import com.bighealth.service.DocumentLoader;
import com.bighealth.service.GraphSearch;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private LLMModel llmModel;

    @Autowired
    private DocumentLoader documentLoader;

    @Autowired
    private GraphSearch graphSearch;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        StreamingChatLanguageModel model =
                llmModel.buildStreamingModel();
        String systemPrompt = documentLoader.readSystemPrompt();
        String secondaryPrompt = documentLoader.readSecondaryPrompt();
        registry.addHandler(new CustomWebSocketHandler(model, systemPrompt,
                        secondaryPrompt, graphSearch),
                "/chat").setAllowedOrigins("http://localhost:3000") // Allow requests from localhost:3000
                .addInterceptors(new HttpSessionHandshakeInterceptor());
    }
}
