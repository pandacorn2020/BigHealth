package com.bighealth.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.bighealth.llm.*;
import com.bighealth.service.DocumentLoader;
import com.bighealth.service.GraphSearch;
import com.wisdomdata.jdbc.CloudConnection;
import com.wisdomdata.jdbc.CloudStatement;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.wisdomdata.jdbc.CloudStatement.END_OF_STREAMING_CHAT;
import static dev.langchain4j.data.message.SystemMessage.systemMessage;

@CrossOrigin(origins = "*")
@RestController
@SessionAttributes(value = {"statement", "connection"})
public class StreamingChatController {
    //    private static String server = "36.112.210.194:19708";
    private static String server = "127.0.0.1:1978";

    private static CloudConnection conn;
    private static CloudStatement stmt;
    private static final Logger logger = java.util.logging.Logger.getLogger(StreamingChatController.class.getSimpleName());


    private static ConcurrentHashMap<String, HashMap<String, Object>> mapUserData = new ConcurrentHashMap<>();

    private static HashMap<String, String[]> mapChatHistory = new HashMap<>();


    private static String KEY_HISTORY = "history";

    private static String KEY_INPUT = "input";
    private static String KEY_ENTITIES = "entities";

    public static String RAG_QUERY= "ragQuery";


    public static final String INPUT = "input";
    public static final String CHAT_HISTORY = "chatHistory";
    public static final String CHAT = "chat";
    public static final String SECONDARY = "secondary";
    public static final String HISTORY = "history";
    public static final String TOKEN = "token";
    public static final String ENTITIES = "entities";
    public static final String QUERY = "query";
    public static final String NEXT = "next";
    //    private static StreamingChatLanguageModel streamingChatModel;
//
//    private static String systemPrompt;
//
//    private static String secondaryPrompt;
//
    @Autowired
    private GraphSearch graphSearch;

    // create timed out cache
    private static final int CHAT_TIMEOUT_SECONDS = 300;

    @Autowired
    private ExecutorService executorService;  // 假设你有一个线程池

    @Autowired
    private LLMModel llmModel;


    @Autowired
    private DocumentLoader documentLoader;


    interface Assistant {

        TokenStream chat(String userMessage);
    }

    @ResponseBody
    @PostMapping("/askgraph/select")
    public JSONObject askNumberSelect(@RequestBody String body, HttpSession session) throws SQLException, IOException {
        JSONObject obj = JSON.parseObject(body);
        String reqContent = obj.getString("content");

        JSONObject jsonObject = JSON.parseObject(reqContent);
        String userId = obj.getString("from_id");
//        stmt.execute("use " + schema + ";");
        JSONObject resp = new JSONObject();

        if (userId == null || userId.trim().equals("")) {
            resp.put("content", "fail: userId or schema is null");

            return resp;
        }

        Object o = mapUserData.get(userId);
        HashMap<String, Object> userData;
        if (o == null) {
            userData = new HashMap<>();
        } else {
            userData = mapUserData.get(userId);
        }
        userData.put(KEY_HISTORY, new ChatMessage[0]);
        userData.put(KEY_INPUT, "");
        userData.put(KEY_ENTITIES, new String[0]);
        mapUserData.put(userId, userData);

        JSONArray content = new JSONArray();
        resp.put("content", "success");

        return resp;
    }


    @GetMapping(value = "/stream-graph-chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamGraphChat(@RequestParam String id, @RequestParam String query, @RequestParam String input, HttpSession session) throws SQLException {
        String userId = id;
        logger.info("userId:" + userId);
        logger.info("query:" + query);
        logger.info("inputText:" + input);


        SseEmitter emitter = new SseEmitter(1200000L);  // 设置超时为 1200 秒
        emitter.onTimeout(() -> {
            logger.warning("超时了！");
            emitter.complete();
        });
        long startTime = System.currentTimeMillis();
        try {

            if (userId == null || userId.trim().equals("") || input == null || input.trim().equals("") || query == null || query.trim().equals("")) {
                throw new Exception("userId或inputText或query为空");
            }
            HashMap<String, Object> userData = mapUserData.get(userId);
            logger.log(Level.INFO, "userData: {0}", new Object[]{userData});
            ChatMessage[] chatHistory = (ChatMessage[]) userData.get(KEY_HISTORY);


            String systemPrompt = documentLoader.readSystemPrompt();

            StreamingChatLanguageModel streamingChatModel = llmModel.buildStreamingModel();

            ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
            SystemMessage sysMessage = systemMessage(systemPrompt);


            // 使用线程池来执行任务，避免每次都创建新的线程
            executorService.submit(() -> {
                try {

                    // get json array from json node for HISTORY
                    setHistoryMessages(chatMemory, sysMessage, chatHistory);
                    SessionData sessionData = new SessionData();
                    sessionData.setUserData(userData);
                    MeasureTools measureTools = new MeasureTools(sessionData, graphSearch);

                    // cast it as a string array
                    StreamingChatController.Assistant assistant = AiServices.builder(StreamingChatController.Assistant.class)
                            .streamingChatLanguageModel(streamingChatModel)
                            .chatMemory(chatMemory)
                            .tools(measureTools)
                            .build();
                    long beginLlmRequestStart = System.currentTimeMillis();
                    final String[] reportString = {""};
                    assistant.chat(input)
                            .onNext(new Consumer<String>() {
                                @Override
                                public void accept(String s) {
                                    logger.info(s);
                                    reportString[0] += s;
                                    if (s.equals(END_OF_STREAMING_CHAT)) {
                                        emitter.complete();
                                    }
                                    try {
                                        emitter.send(s + "\n\n");
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                            })
                            .onComplete(new Consumer<Response<AiMessage>>() {
                                @Override
                                public void accept(Response<AiMessage> aiMessageResponse) {


                                    emitter.complete();

                                    ChatMessage[] currentChatHistory = getChatHistory(chatMemory);
                                    userData.put(KEY_HISTORY, currentChatHistory);
                                    userData.put(KEY_INPUT, input);
    //                                        userData.put(KEY_ENTITIES, sessionData.getRagQuery().getEntities());
//                                    logger.info("history: " + currentChatHistory);
                                    logger.info("\n结束了");
                                    logger.info("问题：" + input);
                                    logger.info("用户数据：" + userData);
                                    logger.info("首Token耗时：" + (System.currentTimeMillis() - beginLlmRequestStart) + "ms");
                                    logger.info("响应耗时：" + (System.currentTimeMillis() - startTime) + "ms");
                                    logger.info("报告：\n" + reportString[0]);
                                }
                            })
                            .onError(new Consumer<Throwable>() {
                                @Override
                                public void accept(Throwable throwable) {
                                    logger.warning("发生错误：" + throwable.getMessage());
                                    emitter.complete();
                                }
                            }).start();
//                    emitter.complete();  // 完成流式响应
                } catch (Exception e) {
                    logger.warning("发生错误：" + e.getMessage());
                    emitter.completeWithError(e);  // 如果发生错误，通知客户端
                }
            });
        } catch (Exception e) {
            logger.warning("发生错误：" + e.getMessage());
            emitter.completeWithError(e);  // 如果外部异常发生，通知客户端
        }

        return emitter;
    }


    public static void setHistoryMessages(ChatMemory chatMemory,
                                          SystemMessage systemMessage, ChatMessage[] messages) {
        chatMemory.clear();
        if (systemMessage != null) {
            chatMemory.add(systemMessage);
        }
        for (ChatMessage message : messages) {
            chatMemory.add(message);
        }
    }

    private static ToolExecutionResultMessage buildToolExecutionResultMessage(String message) {
        int index = message.indexOf("id = ");
        int index1 = message.indexOf("toolName = ", index + 1);
        int index2 = message.indexOf("text = ", index1 + 1);
        String id = unquoted(message.substring(index + 6, index1 - 3).trim());
        String toolName = unquoted(message.substring(index1 + 11, index2 - 3).trim());
        String text = unquoted(message.substring(index2 + 8, message.length() - 2).trim());
        return new ToolExecutionResultMessage(id, toolName, text);
    }

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

    private ChatMessage[] getChatHistory(ChatMemory chatMemory) {
        if (chatMemory == null) {
            return new ChatMessage[0];
        }
        List<ChatMessage> messages = chatMemory.messages();
        List<ChatMessage> history = new ArrayList();
        // 将messages转换为字符串数组

        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message instanceof SystemMessage) {
                continue;
            }
            history.add(message);
        }
        return history.toArray(new ChatMessage[0]);
    }


}
