package com.nova.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nova.ai.entity.*;
import com.nova.ai.exception.ResourceNotFoundException;
import com.nova.ai.repository.ChatHistoryRepository;
import com.nova.ai.repository.MemoryRepository;
import com.nova.ai.repository.MessageRepository;
import com.nova.ai.service.OllamaLocalService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class OllamaLocalServiceImpl implements OllamaLocalService {

    private final ChatHistoryRepository chatHistoryRepository;
    private final MessageRepository messageRepository;
    private final MemoryRepository memoryRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final HttpClient httpClient;

    @Value("${nova.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${nova.ollama.default-model}")
    private String defaultModel;

    public OllamaLocalServiceImpl(ChatHistoryRepository chatHistoryRepository,
                                  MessageRepository messageRepository,
                                  MemoryRepository memoryRepository,
                                  ObjectMapper objectMapper,
                                  PlatformTransactionManager transactionManager) {
        this.chatHistoryRepository = chatHistoryRepository;
        this.messageRepository = messageRepository;
        this.memoryRepository = memoryRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .build();
    }

    @Override
    public String getChatResponse(User user, String chatId, String userMessage) {
        try {
            if (!isOllamaRunning()) {
                throw new RuntimeException("Ollama service is not running or unreachable. Please start the Ollama server.");
            }

            // Save user message
            transactionTemplate.executeWithoutResult(status -> {
                ChatHistory chat = chatHistoryRepository.findById(chatId)
                        .orElseThrow(() -> new ResourceNotFoundException("Chat history not found"));

                Message userMsg = Message.builder()
                        .chat(chat)
                        .role("user")
                        .content(userMessage)
                        .build();
                messageRepository.save(userMsg);
                chat.getMessages().add(userMsg);
                chat.setUpdatedAt(LocalDateTime.now());
                chatHistoryRepository.save(chat);
            });

            // Get available models
            List<String> available = getAvailableModels();

            // Build payload
            String payload = transactionTemplate.execute(status -> {
                try {
                    ChatHistory chat = chatHistoryRepository.findById(chatId)
                            .orElseThrow(() -> new ResourceNotFoundException("Chat history not found"));
                    return buildChatPayload(user, chat, false, available);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            String activeUrl = getActiveOllamaUrl();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(activeUrl + "/api/chat"))
                    .header("Content-Type", "application/json")
                    .timeout(java.time.Duration.ofMinutes(3))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to call Ollama. Status: " + response.statusCode());
            }

            JsonNode node = objectMapper.readTree(response.body());
            String assistantResponse = node.get("message").get("content").asText();

            // Save assistant response
            transactionTemplate.executeWithoutResult(status -> {
                ChatHistory chat = chatHistoryRepository.findById(chatId)
                        .orElseThrow(() -> new ResourceNotFoundException("Chat history not found"));

                Message assistantMsg = Message.builder()
                        .chat(chat)
                        .role("assistant")
                        .content(assistantResponse)
                        .build();
                messageRepository.save(assistantMsg);
                chat.getMessages().add(assistantMsg);
                chatHistoryRepository.save(chat);
            });

            return assistantResponse;

        } catch (Exception e) {
            log.error("Error in getChatResponse", e);
            throw new RuntimeException("Ollama request failed: " + e.getMessage());
        }
    }

    @Override
    public void streamChatResponse(User user, String chatId, String userMessage, SseEmitter emitter) {
        log.info("[DEBUG] OllamaLocalServiceImpl: streamChatResponse called for chatId={}, userMessage='{}'", chatId, userMessage);
        executorService.submit(() -> {
            log.info("[DEBUG] OllamaLocalServiceImpl async thread: started processing for chatId={}", chatId);
            try {
                // Get available models first (non-blocking for DB)
                List<String> available = getAvailableModels();
                log.info("[DEBUG] OllamaLocalServiceImpl async thread: available models={}", available);

                // Save user message in a transaction context
                log.info("[DEBUG] OllamaLocalServiceImpl async thread: saving user message to database");
                transactionTemplate.executeWithoutResult(status -> {
                    ChatHistory chat = chatHistoryRepository.findById(chatId)
                            .orElseThrow(() -> new ResourceNotFoundException("Chat history not found"));

                    log.info("[DEBUG] OllamaLocalServiceImpl: DB values before user message save. Messages in chat: count={}", chat.getMessages().size());
                    for (Message m : chat.getMessages()) {
                        log.info("[DEBUG] OllamaLocalServiceImpl:   msg id={}, role={}, contentLength={}, createdAt={}", m.getId(), m.getRole(), m.getContent().length(), m.getCreatedAt());
                    }

                    Message userMsg = Message.builder()
                            .chat(chat)
                            .role("user")
                            .content(userMessage)
                            .build();
                    messageRepository.save(userMsg);
                    chat.getMessages().add(userMsg);
                    chat.setUpdatedAt(LocalDateTime.now());
                    chatHistoryRepository.save(chat);

                    log.info("[DEBUG] OllamaLocalServiceImpl: DB values after user message save. Messages in chat: count={}", chat.getMessages().size());
                    for (Message m : chat.getMessages()) {
                        log.info("[DEBUG] OllamaLocalServiceImpl:   msg id={}, role={}, contentLength={}, createdAt={}", m.getId(), m.getRole(), m.getContent().length(), m.getCreatedAt());
                    }
                });
                log.info("[DEBUG] OllamaLocalServiceImpl async thread: user message saved successfully");

                if (!isOllamaRunning()) {
                    log.warn("[DEBUG] OllamaLocalServiceImpl async thread: Ollama service is not running");
                    emitter.send(SseEmitter.event().name("error").data("Ollama service is not running or unreachable. Please start the Ollama server."));
                    emitter.complete();
                    return;
                }

                // Build payload (requires database read of chat messages, run inside transaction)
                log.info("[DEBUG] OllamaLocalServiceImpl async thread: building chat payload");
                String payload = transactionTemplate.execute(status -> {
                    try {
                        ChatHistory chat = chatHistoryRepository.findById(chatId)
                                .orElseThrow(() -> new ResourceNotFoundException("Chat history not found"));
                        return buildChatPayload(user, chat, true, available);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                log.info("[DEBUG] OllamaLocalServiceImpl async thread: payload built length={}", payload.length());

                String activeUrl = getActiveOllamaUrl();
                log.info("[DEBUG] OllamaLocalServiceImpl async thread: active Ollama URL={}", activeUrl);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(activeUrl + "/api/chat"))
                        .header("Content-Type", "application/json")
                        .timeout(java.time.Duration.ofMinutes(3))
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                log.info("[DEBUG] OllamaLocalServiceImpl async thread: sending HTTP request to Ollama");
                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                log.info("[DEBUG] OllamaLocalServiceImpl async thread: Ollama response status code={}", response.statusCode());

                if (response.statusCode() != 200) {
                    emitter.send(SseEmitter.event().name("error").data("Ollama server returned error code " + response.statusCode()));
                    emitter.complete();
                    return;
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    String line;
                    StringBuilder fullAssistantResponse = new StringBuilder();
                    log.info("[DEBUG] OllamaLocalServiceImpl async thread: start reading and streaming response chunks");

                    while ((line = reader.readLine()) != null) {
                        if (line.trim().isEmpty()) continue;

                        JsonNode jsonNode = objectMapper.readTree(line);
                        if (jsonNode.has("message") && jsonNode.get("message").has("content")) {
                            String content = jsonNode.get("message").get("content").asText();
                            log.info("[DEBUG] OllamaLocalServiceImpl: received chunk content='{}'", content);
                            fullAssistantResponse.append(content);

                            // Send JSON wrapped chunk to client to preserve spaces and newlines
                            ObjectNode chunkNode = objectMapper.createObjectNode();
                            chunkNode.put("content", content);
                            String chunkStr = objectMapper.writeValueAsString(chunkNode);
                            log.info("[DEBUG] OllamaLocalServiceImpl: sending chunk to emitter chunk='{}'", chunkStr);
                            emitter.send(SseEmitter.event().data(chunkStr));
                        }

                        if (jsonNode.has("done") && jsonNode.get("done").asBoolean()) {
                            log.info("[DEBUG] OllamaLocalServiceImpl async thread: done parsing response chunks");
                            break;
                        }
                    }

                    // Save assistant message to DB
                    final String assistantText = fullAssistantResponse.toString();
                    log.info("[DEBUG] OllamaLocalServiceImpl async thread: saving assistant response to database length={}", assistantText.length());
                    transactionTemplate.executeWithoutResult(status -> {
                        ChatHistory chat = chatHistoryRepository.findById(chatId)
                                .orElseThrow(() -> new ResourceNotFoundException("Chat history not found"));

                        log.info("[DEBUG] OllamaLocalServiceImpl: DB values before assistant message save. Messages in chat: count={}", chat.getMessages().size());
                        for (Message m : chat.getMessages()) {
                            log.info("[DEBUG] OllamaLocalServiceImpl:   msg id={}, role={}, contentLength={}, createdAt={}", m.getId(), m.getRole(), m.getContent().length(), m.getCreatedAt());
                        }

                        Message assistantMsg = Message.builder()
                                .chat(chat)
                                .role("assistant")
                                .content(assistantText)
                                .build();
                        messageRepository.save(assistantMsg);
                        chat.getMessages().add(assistantMsg);
                        chatHistoryRepository.save(chat);

                        log.info("[DEBUG] OllamaLocalServiceImpl: DB values after assistant message save. Messages in chat: count={}", chat.getMessages().size());
                        for (Message m : chat.getMessages()) {
                            log.info("[DEBUG] OllamaLocalServiceImpl:   msg id={}, role={}, contentLength={}, createdAt={}", m.getId(), m.getRole(), m.getContent().length(), m.getCreatedAt());
                        }
                    });
                    log.info("[DEBUG] OllamaLocalServiceImpl async thread: assistant response saved successfully");

                    log.info("[DEBUG] OllamaLocalServiceImpl async thread: sending [DONE] to emitter");
                    emitter.send(SseEmitter.event().data("[DONE]"));
                    emitter.complete();
                    log.info("[DEBUG] OllamaLocalServiceImpl async thread: stream completed and emitter completed");
                }

            } catch (Exception e) {
                log.error("[DEBUG] OllamaLocalServiceImpl: Error in streaming response from Ollama", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("Connection error: " + e.getMessage()));
                } catch (Exception ex) {
                    // Ignore
                }
                emitter.completeWithError(e);
            }
        });
    }

    @Override
    public List<String> getAvailableModels() {
        List<String> models = new ArrayList<>();
        try {
            if (!isOllamaRunning()) {
                return models;
            }
            String activeUrl = getActiveOllamaUrl();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(activeUrl + "/api/tags"))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                if (root.has("models")) {
                    for (JsonNode modelNode : root.get("models")) {
                        models.add(modelNode.get("name").asText());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to query Ollama models list", e);
        }
        return models;
    }

    @Override
    public String generateText(String prompt, String model, double temperature) {
        try {
            List<String> available = getAvailableModels();
            String activeModel = resolveModelName(model, available);

            ObjectNode rootNode = objectMapper.createObjectNode();
            rootNode.put("model", activeModel);
            rootNode.put("prompt", prompt);
            rootNode.put("stream", false);

            ObjectNode options = objectMapper.createObjectNode();
            options.put("temperature", temperature);
            rootNode.set("options", options);

            String activeUrl = getActiveOllamaUrl();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(activeUrl + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .timeout(java.time.Duration.ofMinutes(1))
                    .POST(HttpRequest.BodyPublishers.ofString(rootNode.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode resNode = objectMapper.readTree(response.body());
                return resNode.get("response").asText();
            }
            throw new RuntimeException("Ollama text generation failed: " + response.statusCode());
        } catch (Exception e) {
            log.error("Error generating text", e);
            return "Error calling LLM offline service: " + e.getMessage();
        }
    }

    @Override
    public boolean isOllamaRunning() {
        if (checkOllama(ollamaBaseUrl)) {
            return true;
        }
        if (ollamaBaseUrl.contains("localhost")) {
            String alternateUrl = ollamaBaseUrl.replace("localhost", "127.0.0.1");
            return checkOllama(alternateUrl);
        }
        return false;
    }

    private String getActiveOllamaUrl() {
        if (checkOllama(ollamaBaseUrl)) {
            return ollamaBaseUrl;
        }
        if (ollamaBaseUrl.contains("localhost")) {
            String alternateUrl = ollamaBaseUrl.replace("localhost", "127.0.0.1");
            if (checkOllama(alternateUrl)) {
                return alternateUrl;
            }
        }
        return ollamaBaseUrl;
    }

    private boolean checkOllama(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private String resolveModelName(String modelName, List<String> available) {
        String activeModel = "gemma3:4b";
        if (available != null && !available.isEmpty() && !available.contains(activeModel)) {
            boolean found = false;
            for (String model : available) {
                if (model.startsWith(activeModel) || activeModel.startsWith(model)) {
                    activeModel = model;
                    found = true;
                    break;
                }
            }
            if (!found) {
                activeModel = available.get(0);
            }
        }
        return activeModel;
    }

    private String buildChatPayload(User user, ChatHistory chat, boolean isStream, List<String> available) throws Exception {
        Settings settings = user.getSettings();
        String activeModel = settings != null ? settings.getModelName() : defaultModel;

        activeModel = resolveModelName(activeModel, available);

        double temp = settings != null ? settings.getTemperature() : 0.7;
        double topP = settings != null ? settings.getTopP() : 0.9;
        int maxTok = settings != null ? settings.getMaxTokens() : 2048;

        ObjectNode payloadNode = objectMapper.createObjectNode();
        payloadNode.put("model", activeModel);
        payloadNode.put("stream", isStream);

        ObjectNode optionsNode = objectMapper.createObjectNode();
        optionsNode.put("temperature", temp);
        optionsNode.put("top_p", topP);
        optionsNode.put("num_predict", maxTok);
        payloadNode.set("options", optionsNode);

        List<Memory> memories = new ArrayList<>();
        if (settings != null && settings.getMemoryEnabled()) {
            memories = memoryRepository.findByUserId(user.getId());
        }

        ArrayNode messagesArray = objectMapper.createArrayNode();

        ObjectNode systemMsg = objectMapper.createObjectNode();
        systemMsg.put("role", "system");
        systemMsg.put("content", buildSystemPrompt(user, memories));
        messagesArray.add(systemMsg);

        List<Message> history = chat.getMessages();
        int startIndex = Math.max(0, history.size() - 20);
        for (int i = startIndex; i < history.size(); i++) {
            Message msg = history.get(i);
            ObjectNode chatMsg = objectMapper.createObjectNode();
            chatMsg.put("role", msg.getRole());
            chatMsg.put("content", msg.getContent());
            messagesArray.add(chatMsg);
        }

        payloadNode.set("messages", messagesArray);
        return payloadNode.toString();
    }

    private String buildSystemPrompt(User user, List<Memory> memories) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are NOVA (Networked Organism & Visual Assistant), a super-intelligent offline personal AI assistant similar to Jarvis.\n");
        sb.append("You are running locally on the user's computer. Be helpful, concise, professional, and friendly.\n");
        sb.append("Format all code blocks with correct markdown indicating the programming language (e.g. ```javascript).\n");

        if (user.getSettings().getMemoryEnabled() && memories != null && !memories.isEmpty()) {
            sb.append("\n[PROFILE & MEMORIES]\n");
            sb.append("Here is what you know about the user (use these details contextually where relevant to personalize responses):\n");
            for (Memory m : memories) {
                sb.append("- ").append(m.getCategory().toUpperCase()).append(": ").append(m.getContent()).append("\n");
            }
        }
        return sb.toString();
    }
}
