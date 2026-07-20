package com.nova.ai.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nova.ai.entity.User;
import com.nova.ai.repository.UserRepository;
import com.nova.ai.security.JwtTokenProvider;
import com.nova.ai.service.OllamaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

@Component
@Slf4j
public class NovaWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final OllamaService ollamaService;

    public NovaWebSocketHandler(ObjectMapper objectMapper,
                                 JwtTokenProvider tokenProvider,
                                 UserRepository userRepository,
                                 @Lazy OllamaService ollamaService) {
        this.objectMapper = objectMapper;
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.ollamaService = ollamaService;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            JsonNode payloadNode = objectMapper.readTree(message.getPayload());
            String token = payloadNode.has("token") ? payloadNode.get("token").asText() : "";
            String chatId = payloadNode.has("chatId") ? payloadNode.get("chatId").asText() : "";
            String userMsg = payloadNode.has("message") ? payloadNode.get("message").asText() : "";

            if (token.isEmpty() || !tokenProvider.validateToken(token)) {
                session.sendMessage(new TextMessage("{\"error\": \"Unauthorized. Invalid JWT Token.\"}"));
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }

            String username = tokenProvider.getUsernameFromJwt(token);
            User user = userRepository.findByUsername(username).orElse(null);
            if (user == null) {
                session.sendMessage(new TextMessage("{\"error\": \"User profile not found.\"}"));
                return;
            }

            // Execute response generation in a detached thread to prevent socket blocking
            new Thread(() -> {
                try {
                    session.sendMessage(new TextMessage("{\"status\": \"thinking\"}"));
                    
                    // Call Ollama chat to generate the response and save it
                    String responseText = ollamaService.getChatResponse(user, chatId, userMsg);
                    
                    TextMessage output = new TextMessage(objectMapper.createObjectNode()
                            .put("content", responseText)
                            .put("status", "done")
                            .toString());
                    session.sendMessage(output);
                } catch (Exception e) {
                    log.error("WebSocket transaction error", e);
                    try {
                        session.sendMessage(new TextMessage("{\"error\": \"" + e.getMessage() + "\"}"));
                    } catch (IOException io) {
                        // ignore
                    }
                }
            }).start();

        } catch (Exception e) {
            log.error("Failed to parse socket command", e);
            session.sendMessage(new TextMessage("{\"error\": \"Invalid command syntax: " + e.getMessage() + "\"}"));
        }
    }
}
