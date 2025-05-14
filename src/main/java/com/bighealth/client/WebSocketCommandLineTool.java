package com.bighealth.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdomdata.jdbc.CloudStatement;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;

public class WebSocketCommandLineTool {

    private static final String WEBSOCKET_URL = "ws://localhost:8080/chat"; // Replace with your WebSocket URL
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> history = new ArrayList<>();
    private static CountDownLatch latch;

    public static void main(String[] args) {
        HttpClient client = HttpClient.newHttpClient();
        WebSocket webSocket = client.newWebSocketBuilder()
                .buildAsync(URI.create(WEBSOCKET_URL), new WebSocketListener())
                .join();

        Scanner scanner = new Scanner(System.in);
        System.out.println("Connected to WebSocket server. Type your message:");

        while (true) {
            System.out.print("user> "); // Display "user>" prompt
            String userInput = scanner.nextLine();
            if ("exit".equalsIgnoreCase(userInput)) {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Goodbye!").join();
                System.out.println("Connection closed.");
                break;
            }

            try {
                // Initialize the latch to wait for the server response
                latch = new CountDownLatch(1);

                // Create a JSON message with input and history
                String jsonMessage = objectMapper.writeValueAsString(createMessage(userInput));
                webSocket.sendText(jsonMessage, true);

                // Wait for the server response to finish
                latch.await();
            } catch (Exception e) {
                System.err.println("Error sending message: " + e.getMessage());
            }
        }

        scanner.close();
    }

    private static class WebSocketListener implements WebSocket.Listener {

        @Override
        public void onOpen(WebSocket webSocket) {
            System.out.println("WebSocket connection established.");
            WebSocket.Listener.super.onOpen(webSocket);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            try {
                // Parse the received JSON message
                JsonNode response = objectMapper.readTree(data.toString());

                // Extract and display only the "token" field
                if (response.has("token")) {
                    String token = response.get("token").asText();


                    // Check if the token indicates the end of the response
                    if (CloudStatement.END_OF_STREAMING_CHAT.equals(token)) {
                        if (response.has("history")) {
                            JsonNode historyNode = response.get("history");
                            if (historyNode.isArray()) {
                                history.clear(); // Clear the existing history
                                for (JsonNode historyItem : historyNode) {
                                    history.add(historyItem.asText());
                                }
                            }
                        }
                        System.out.println("");
                        latch.countDown(); // Release the latch to allow the next input
                    } else {
                        System.out.print(token);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error processing message: " + e.getMessage());
            }
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            System.out.println("WebSocket closed with status " + statusCode + " and reason: " + reason);
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            System.err.println("WebSocket error: " + error.getMessage());
        }
    }

    private static Message createMessage(String userInput) {
        Message message = new Message();
        message.setInput(userInput);
        message.setHistory(new ArrayList<>(history));
        return message;
    }

    private static class Message {
        private String input;
        private List<String> history;

        public String getInput() {
            return input;
        }

        public void setInput(String input) {
            this.input = input;
        }

        public List<String> getHistory() {
            return history;
        }

        public void setHistory(List<String> history) {
            this.history = history;
        }
    }
}