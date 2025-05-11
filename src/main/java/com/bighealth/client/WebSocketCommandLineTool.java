package com.bighealth.client;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.*;

public class WebSocketCommandLineTool {

    private static final String SERVER_URI = "ws://localhost:8080/chat"; // Replace with your WebSocket server URI
    private static final String END_OF_STREAMING_CHAT = "END_OF_STREAMING_CHAT"; // CloudStatement.END_OF_STREAMING_CHAT equivalent

    private static String input;
    private static String[] history;

    public static void main(String[] args) {
        try {
            WebSocketClient client = new WebSocketClient(new URI(SERVER_URI)) {
                @Override
                public void onOpen(ServerHandshake handshake) {

                    String userInput = "经常有疲劳感、前胸有湿疹；";
                    //while (userInput != null && !userInput.isEmpty()) {
                        input = userInput.trim();
                        // Send the input to the server
                        Map<String, Object> map = new HashMap<>();
                        map.put("input", input);
                        map.put("history", history);
                        ObjectMapper objectMapper = new ObjectMapper();
                        try {
                            String json = objectMapper.writeValueAsString(map);
                            send(json);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                        //userInput = scanner.nextLine();
                    //}
                }

                @Override
                public void onMessage(String message) {
                    try {
                        // Parse the JSON response
                        ObjectMapper objectMapper = new ObjectMapper();
                        JsonNode jsonNode = objectMapper.readTree(message);

                        // Extract the token
                        if (jsonNode.has("token")) {
                            String token = jsonNode.get("token").asText();
                            System.out.println(token);

                            // Check for end of streaming
                            if (END_OF_STREAMING_CHAT.equals(token)) {
                                if (jsonNode.has("history") && jsonNode.get("history").isArray()) {
                                    List<String> historyList = new ArrayList<>();
                                    for (JsonNode element : jsonNode.get("history")) {
                                        historyList.add(element.asText());
                                    }
                                    history = historyList.toArray(new String[0]);
                                }
                                close();
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error processing message: " + e.getMessage());
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("Connection closed: " + reason);
                }

                @Override
                public void onError(Exception ex) {
                    System.err.println("WebSocket error: " + ex.getMessage());
                }
            };

            // Connect to the WebSocket server
            client.setConnectionLostTimeout(300);
            client.connect();

            // Keep the program running until the connection is closed
            while (!client.isClosed()) {
                Thread.sleep(100);
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}