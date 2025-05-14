package com.bighealth.llm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bighealth.service.GraphSearch;
import com.wisdomdata.jdbc.CloudStatement;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import org.apache.logging.log4j.Logger;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.function.Consumer;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;

public class CustomWebSocketHandler extends TextWebSocketHandler {

    public static final String INPUT = "input";
    public static final String CHAT_HISTORY = "chatHistory";
    public static final String CHAT = "chat";
    public static final String SECONDARY = "secondary";
    public static final String HISTORY = "history";
    public static final String TOKEN = "token";
    public static final String ENTITIES = "entities";
    public static final String QUERY = "query";
    public static final String NEXT = "next";

    private static Logger logger = org.apache.logging.log4j.LogManager.getLogger(CustomWebSocketHandler.class.getSimpleName());
    interface Assistant {

        TokenStream chat(String userMessage);
    }

    private StreamingChatLanguageModel streamingChatModel;

    private String systemPrompt;

    private String secondaryPrompt;

    private GraphSearch graphSearch;

    // create timed out cache
    private static final int CHAT_TIMEOUT_SECONDS = 300;

    public CustomWebSocketHandler(StreamingChatLanguageModel streamingChatModel,
                                   String systemPrompt,
                                  String secondaryPrompt,
                                  GraphSearch graphSearch) {
        this.streamingChatModel = streamingChatModel;
        this.systemPrompt = systemPrompt;
        this.secondaryPrompt = secondaryPrompt;
        this.graphSearch = graphSearch;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("New connection: " + session.getId());
        // session.sendMessage(new TextMessage("{\"status\":\"connected\"}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(payload);
        handleChat(session, jsonNode);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // Clean up chat history when session closes
        System.out.println("Connection closed: " + status);
    }

    public static String[] convertJsonArrayToStringArray(JsonNode jsonNode, String arrayFieldName) {
        List<String> list = new ArrayList<>();
        JsonNode arrayNode = jsonNode.get(arrayFieldName);
        if (arrayNode != null && arrayNode.isArray()) {
            for (JsonNode element : arrayNode) {
                list.add(element.asText());
            }
        }
        return list.toArray(new String[0]);
    }

    public void handleChat(WebSocketSession session, JsonNode jsonNode) {
        String input = jsonNode.get(INPUT).asText();
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(8);
        SystemMessage sysMessage = systemMessage(systemPrompt);
        // get json array from json node for HISTORY
        String[] historyMessages = convertJsonArrayToStringArray(jsonNode, HISTORY);
        setHistoryMessages(chatMemory, sysMessage, historyMessages);
        SessionData sessionData = new SessionData();
        sessionData.setChatMemory(chatMemory);
        MeasureTools measureTools = new MeasureTools(sessionData, graphSearch);
        // cast it as a string array
        Assistant assistant = AiServices.builder(Assistant.class)
                .streamingChatLanguageModel(streamingChatModel)
                .chatMemory(chatMemory)
                .tools(measureTools)
                .build();
        assistant.chat(input)
                .onNext(new ConsumerToken(session, sessionData))
                .onComplete(new ConsumerAiMessage(session, sessionData))
                .onError(new ConsumerError(session, sessionData)).start();
    }

    class ConsumerToken implements Consumer<String>
    {
        private WebSocketSession session;
        private SessionData sessionData;

        public ConsumerToken(WebSocketSession session, SessionData sessionData) {
            this.session = session;
            this.sessionData = sessionData;
        }

        @Override
        public void accept(String token) {
            try {
                logger.info("send token to client: {}", token);
                sendMessage(session, token);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }

    class ConsumerError implements Consumer<Throwable> {

        private WebSocketSession session;

        private SessionData sessionData;

        public ConsumerError(WebSocketSession session, SessionData sessionData) {
            this.session = session;
            this.sessionData = sessionData;
        }
        @Override
        public void accept(Throwable t) {
            try {
                // convert the throwable to a string
                StringWriter writer = new StringWriter();
                PrintWriter pw = new PrintWriter(writer);
                t.printStackTrace(pw);
                pw.flush();
                String message = writer.toString();
                sendMessage(session, message);
                sendDoneMessage(session, sessionData);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void sendMessage(WebSocketSession session, String message) {
        try {
            Map<String, Object> map = new HashMap<>();
            map.put(TOKEN, message);
            ObjectMapper objectMapper = new ObjectMapper();
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(map)));
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private void sendDoneMessage(WebSocketSession session, SessionData sessionData) {
        try {
            Map<String, Object> map = new HashMap<>();
            map.put(TOKEN, CloudStatement.END_OF_STREAMING_CHAT);
            map.put(HISTORY, getChatHistory(sessionData.getChatMemory()));
            RagQuery ragQuery = sessionData.getRagQuery();
            map.put(INPUT, ragQuery != null ? ragQuery.getQuery() : null);
            map.put(ENTITIES, ragQuery != null ? ragQuery.getEntities() : null);
            ObjectMapper objectMapper = new ObjectMapper();
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(map)));
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }


    class ConsumerAiMessage implements Consumer<Response<AiMessage>> {

        private WebSocketSession session;

        private SessionData sessionData;

        public ConsumerAiMessage(WebSocketSession session, SessionData sessionData) {
            this.session = session;
            this.sessionData = sessionData;
        }

        @Override
        public void accept(Response<AiMessage> response) {
            try {
                sendDoneMessage(session, sessionData);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }


    private List<String> getChatHistory(ChatMemory chatMemory) {
        if (chatMemory == null) {
            return Collections.emptyList();
        }
        List<ChatMessage> messages = chatMemory.messages();
        List<String> history = new ArrayList<>();
        for (ChatMessage message : messages) {
            if (message instanceof SystemMessage) {
                continue;
            }
            history.add(message.toString());
        }
        return history;
    }

    public static void setHistoryMessages(ChatMemory chatMemory,
                                          SystemMessage systemMessage, String[] messages) {
        chatMemory.clear();
        if (systemMessage != null) {
            chatMemory.add(systemMessage);
        }
        for (int i = 0; i < messages.length; i++) {
            String message = messages[i].trim();
            if (message.startsWith("UserMessage")) {
                chatMemory.add(buildUserMessage(message));
            } else if (message.startsWith("AiMessage")) {
                chatMemory.add(buildAiMessage(message));
            } else if (message.startsWith("ToolExecutionResultMessage")) {
                chatMemory.add(buildToolExecutionResultMessage(message));
            }
        }
    }

    /**
     * "UserMessage {" +
     *                 " name = " + quoted(name) +
     *                 " contents = " + contents +
     *                 " }"
     * @return
     */
    private static UserMessage buildUserMessage(String message) {
        int index = message.indexOf(" name = ");
        if (index > 0) {
            int index1 = message.indexOf(" contents = ", index + 1);
            String name = message.substring(index + 8, index1 - 1).trim();
            if (name.startsWith("\"") && name.endsWith("\"")) {
                name = name.substring(1, name.length() - 1);
            }
            String content = message.substring(index1 + 12, message.length() - 2).trim();
            content = content.substring(1, content.length() - 1);
            return UserMessage.userMessage(name, content);
        } else {
            return UserMessage.userMessage(message);
        }
    }

    private static AiMessage buildAiMessage(String message) {
        int index = message.indexOf("text = ");
        int index1 = message.indexOf("toolExecutionRequests = ", index + 1);
        String text = message.substring(index + 8, index1 - 3).trim();
        String tools = message.substring(index1 + 24, message.length() - 2).trim();
        if (tools.equals("null")) {
            return AiMessage.from(text);
        }
        index = tools.indexOf("ToolExecutionRequest {");
        List<ToolExecutionRequest> toolList = new ArrayList<>();
        while (index > 0) {
            int index2 = tools.indexOf("}", index + 1);
            String tool = tools.substring(index, index2 + 1);
            toolList.add(buildToolExecutionRequest(tool));
            index = tools.indexOf("ToolExecutionRequest {", index + 1);
        }
        return new AiMessage(text, toolList);
    }

    /**
     * "ToolExecutionResultMessage {" +
     *                 " id = " + quoted(id) +
     *                 " toolName = " + quoted(toolName) +
     *                 " text = " + quoted(text) +
     *                 " }";
     * @param message
     * @return
     */
    private static ToolExecutionResultMessage buildToolExecutionResultMessage(String message) {
        int index = message.indexOf("id = ");
        int index1 = message.indexOf("toolName = ", index + 1);
        int index2 = message.indexOf("text = ", index1 + 1);
        String id = unquoted(message.substring(index + 6, index1 - 3).trim());
        String toolName =unquoted(message.substring(index1 + 11, index2 - 3).trim());
        String text = unquoted(message.substring(index2 + 8, message.length() - 2).trim());
        return new ToolExecutionResultMessage(id, toolName, text);
    }

    private static ToolExecutionRequest buildToolExecutionRequest(String tool) {
        int index = tool.indexOf("id = ");
        int index1 = tool.indexOf("name = ", index + 1);
        int index2 = tool.indexOf("arguments = ", index1 + 1);
        String id = unquoted(tool.substring(index + 6, index1 - 3).trim());
        String name = unquoted(tool.substring(index1 + 8, index2 - 3).trim());
        String arguments = unquoted(tool.substring(index2 + 13, tool.length() - 2).trim());
        return ToolExecutionRequest.builder().id(id).name(name).arguments(arguments).build();
    }

    private static String unquoted(String str) {
        if (str.equals("null")) {
            return null;
        }
        if (str.startsWith("'") && str.endsWith("'")) {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }

    class CustomStreamResponseHandler implements StreamingResponseHandler<AiMessage> {
        private ConsumerToken consumerToken;
        private ConsumerAiMessage consumerAiMessage;

        private ConsumerError consumerError;

        public CustomStreamResponseHandler(WebSocketSession session, SessionData sessionData) {
            this.consumerToken = new ConsumerToken(session, sessionData);
            this.consumerAiMessage = new ConsumerAiMessage(session, sessionData);
            this.consumerError = new ConsumerError(session, sessionData);
        }

        @Override
        public void onNext(String token) {
            consumerToken.accept(token);
        }

        @Override
        public void onComplete(Response<AiMessage> response) {
            consumerAiMessage.accept(response);
        }

        @Override
        public void onError(Throwable error) {
            consumerError.accept(error);
        }
    }



}