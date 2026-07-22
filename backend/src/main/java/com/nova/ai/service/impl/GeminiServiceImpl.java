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
import com.nova.ai.service.GeminiService;
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
public class GeminiServiceImpl implements GeminiService {

    private final ChatHistoryRepository chatHistoryRepository;
    private final MessageRepository messageRepository;
    private final MemoryRepository memoryRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final HttpClient httpClient;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Value("${nova.gemini.default-model:gemini-3-flash-preview}")
    private String defaultGeminiModel;

    /**
     * The complete set of currently supported, non-deprecated Gemini model identifiers.
     * Any model NOT in this set will be rejected and replaced with defaultGeminiModel.
     */
    private static final java.util.Set<String> VALID_GEMINI_MODELS = java.util.Set.of(
     "gemini-3-flash-preview"
    );

    /** Deprecated models that unconditionally return HTTP 404 from the Gemini API. */
    private static final java.util.Set<String> DEPRECATED_GEMINI_MODELS = java.util.Set.of(
        "gemini-2.0-flash",
        "gemini-2.0-flash-exp",
        "gemini-2.0-flash-thinking-exp",
        "gemini-3.5-flash",
        "gemini-1.0-pro",
        "gemini-1.0-pro-vision",
        "gemini-pro",
        "gemini-pro-vision"
    );

    public GeminiServiceImpl(ChatHistoryRepository chatHistoryRepository,
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
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String getChatResponse(User user, String chatId, String userMessage) {
        try {
            // 1. Save user message
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

            // 2. Build payload
            String payload = transactionTemplate.execute(status -> {
                try {
                    ChatHistory chat = chatHistoryRepository.findById(chatId)
                            .orElseThrow(() -> new ResourceNotFoundException("Chat history not found"));
                    double temp = (user.getSettings() != null) ? user.getSettings().getTemperature() : 0.7;
                    int maxTok = (user.getSettings() != null) ? user.getSettings().getMaxTokens() : 2048;
                    return buildGeminiPayload(user, chat, temp, maxTok);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            String settingsModel = (user.getSettings() != null) ? user.getSettings().getModelName() : defaultGeminiModel;
            String activeModel = resolveGeminiModel(settingsModel);
            String apiKey = getApiKey();
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + activeModel + ":generateContent?key=" + apiKey;

            log.info("[MODEL] Sync request — stored='{}', resolved='{}', URL model='{}'  (user={})",
                    settingsModel, activeModel, activeModel, user.getUsername());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(java.time.Duration.ofMinutes(3))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Gemini request completed (Sync) - Status: {}", response.statusCode());

            if (response.statusCode() != 200) {
                String errorBody = response.body();
                log.error("Gemini API error (Sync) - HTTP {}: URL={}, Body={}",
                        response.statusCode(), url.replaceAll("key=[^&]+", "key=REDACTED"), errorBody);
                throw new RuntimeException("Failed to call Gemini API. HTTP Status: " + response.statusCode()
                        + ", Model: " + activeModel
                        + ", Response: " + errorBody);
            }

            String responseBody = response.body();
            log.debug("Gemini sync response received ({}  chars)", responseBody.length());
            JsonNode root = objectMapper.readTree(responseBody);
            String assistantResponse = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");

            // 3. Save assistant response
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
            log.error("Error in getChatResponse with Gemini", e);
            throw new RuntimeException("Gemini request failed: " + e.getMessage());
        }
    }

    @Override
    public void streamChatResponse(User user, String chatId, String userMessage, SseEmitter emitter) {
        log.info("[DEBUG] GeminiServiceImpl: streamChatResponse called for chatId={}, userMessage='{}'", chatId, userMessage);
        executorService.submit(() -> {
            log.info("[DEBUG] GeminiServiceImpl async thread: started processing for chatId={}", chatId);
            try {
                // 1. Save user message
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
                log.info("[DEBUG] GeminiServiceImpl async thread: user message saved successfully");

                // 2. Build payload
                String payload = transactionTemplate.execute(status -> {
                    try {
                        ChatHistory chat = chatHistoryRepository.findById(chatId)
                                .orElseThrow(() -> new ResourceNotFoundException("Chat history not found"));
                        double temp = (user.getSettings() != null) ? user.getSettings().getTemperature() : 0.7;
                        int maxTok = (user.getSettings() != null) ? user.getSettings().getMaxTokens() : 2048;
                        return buildGeminiPayload(user, chat, temp, maxTok);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

                String settingsModel = (user.getSettings() != null) ? user.getSettings().getModelName() : defaultGeminiModel;
                String activeModel = resolveGeminiModel(settingsModel);
                String apiKey = getApiKey();
                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + activeModel + ":streamGenerateContent?alt=sse&key=" + apiKey;

                log.info("[MODEL] Stream request — stored='{}', resolved='{}', URL model='{}'  (user={})",
                        settingsModel, activeModel, activeModel, user.getUsername());
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .timeout(java.time.Duration.ofMinutes(3))
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                log.info("Gemini request completed (Stream status) - Status: {}", response.statusCode());

                if (response.statusCode() != 200) {
                    try (InputStream errStream = response.body()) {
                        String errMsg = new String(errStream.readAllBytes(), StandardCharsets.UTF_8);
                        log.error("Gemini API streaming error - HTTP {}: URL={}, Body={}",
                                response.statusCode(),
                                url.replaceAll("key=[^&]+", "key=REDACTED"),
                                errMsg);
                        emitter.send(SseEmitter.event().name("error").data(
                                "Gemini API error HTTP " + response.statusCode()
                                + " for model '" + activeModel + "': " + errMsg));
                    }
                    emitter.complete();
                    return;
                }

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    String line;
                    StringBuilder fullAssistantResponse = new StringBuilder();

                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty()) continue;

                        if (line.startsWith("data: ")) {
                            String dataStr = line.substring(6).trim();
                            JsonNode jsonNode = objectMapper.readTree(dataStr);
                            String content = jsonNode.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
                            
                            if (!content.isEmpty()) {
                                log.debug("[DEBUG] GeminiServiceImpl: received chunk content='{}'", content);
                                fullAssistantResponse.append(content);

                                // Send JSON wrapped chunk to client
                                ObjectNode chunkNode = objectMapper.createObjectNode();
                                chunkNode.put("content", content);
                                String chunkStr = objectMapper.writeValueAsString(chunkNode);
                                emitter.send(SseEmitter.event().data(chunkStr));
                            }
                        }
                    }

                    // Save assistant message to DB
                    final String assistantText = fullAssistantResponse.toString();
                    transactionTemplate.executeWithoutResult(status -> {
                        ChatHistory chat = chatHistoryRepository.findById(chatId)
                                .orElseThrow(() -> new ResourceNotFoundException("Chat history not found"));

                        Message assistantMsg = Message.builder()
                                .chat(chat)
                                .role("assistant")
                                .content(assistantText)
                                .build();
                        messageRepository.save(assistantMsg);
                        chat.getMessages().add(assistantMsg);
                        chatHistoryRepository.save(chat);
                    });
                    log.info("[DEBUG] GeminiServiceImpl async thread: assistant response saved successfully");

                    log.info("[DEBUG] GeminiServiceImpl async thread: sending [DONE] to emitter");
                    emitter.send(SseEmitter.event().data("[DONE]"));
                    emitter.complete();
                }

            } catch (Exception e) {
                log.error("[DEBUG] GeminiServiceImpl: Error in streaming response from Gemini", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("Gemini connection error: " + e.getMessage()));
                } catch (Exception ex) {
                    // Ignore
                }
                emitter.completeWithError(e);
            }
        });
    }

    @Override
    public List<String> getAvailableModels() {
        // Only return models that are confirmed valid on the current Gemini API.
        // gemini-2.0-flash and all other deprecated models are intentionally excluded.
        return List.of( "gemini-3-flash-preview");
    }

    @Override
    public String generateText(String prompt, String model, double temperature) {
        try {
            String activeModel = resolveGeminiModel(model);
            String apiKey = getApiKey();
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + activeModel + ":generateContent?key=" + apiKey;

            ObjectNode payloadNode = objectMapper.createObjectNode();
            ArrayNode contentsArray = objectMapper.createArrayNode();
            ObjectNode contentNode = objectMapper.createObjectNode();
            contentNode.put("role", "user");
            ArrayNode msgParts = objectMapper.createArrayNode();
            ObjectNode msgTextPart = objectMapper.createObjectNode();
            msgTextPart.put("text", prompt);
            msgParts.add(msgTextPart);
            contentNode.set("parts", msgParts);
            contentsArray.add(contentNode);
            payloadNode.set("contents", contentsArray);

            ObjectNode generationConfig = objectMapper.createObjectNode();
            generationConfig.put("temperature", temperature);
            payloadNode.set("generationConfig", generationConfig);

            log.info("Gemini request started (Generate Text) - Model: {}", activeModel);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(java.time.Duration.ofMinutes(1))
                    .POST(HttpRequest.BodyPublishers.ofString(payloadNode.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode resNode = objectMapper.readTree(response.body());
                return resNode.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
            }
            throw new RuntimeException("Gemini text generation failed. HTTP Status: " + response.statusCode() + ", Response: " + response.body());
        } catch (Exception e) {
            log.error("Error generating text with Gemini", e);
            return "Error calling Gemini service: " + e.getMessage();
        }
    }

    private String getApiKey() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException(
                "GEMINI_API_KEY environment variable is not set. " +
                "Please set this variable in your Render service environment settings.");
        }
        return apiKey.trim();
    }

    /**
     * Resolves the final Gemini model name to use for an API request.
     *
     * Resolution rules (in priority order):
     *  1. null / blank                         → defaultGeminiModel
     *  2. Explicitly deprecated Gemini model   → defaultGeminiModel  (logs WARN)
     *  3. Valid known Gemini model              → used as-is          (logs INFO)
     *  4. Unknown gemini-* model               → defaultGeminiModel  (logs WARN)
     *  5. Non-Gemini name (e.g. Ollama model)  → defaultGeminiModel  (logs DEBUG)
     *
     * Three log lines are emitted at the call site (stored / resolved / URL).
     */
    private String resolveGeminiModel(String modelName) {
        // Rule 1: null or blank
        if (modelName == null || modelName.trim().isEmpty()) {
            log.warn("[MODEL] resolveGeminiModel: stored model is null/empty — using default: '{}'", defaultGeminiModel);
            return defaultGeminiModel;
        }

        String trimmed = modelName.trim();

        // Rule 2: explicitly deprecated
        if (DEPRECATED_GEMINI_MODELS.contains(trimmed)) {
            log.warn("[MODEL] resolveGeminiModel: stored='{}' is DEPRECATED (returns HTTP 404) — resolved to default: '{}'",
                    trimmed, defaultGeminiModel);
            return defaultGeminiModel;
        }

        // Rule 3: valid known Gemini model
        if (VALID_GEMINI_MODELS.contains(trimmed)) {
            log.info("[MODEL] resolveGeminiModel: stored='{}' is valid — resolved to: '{}'", trimmed, trimmed);
            return trimmed;
        }

        // Rule 4: unknown gemini-* model (not in our validated set)
        if (trimmed.startsWith("gemini-")) {
            log.warn("[MODEL] resolveGeminiModel: stored='{}' starts with 'gemini-' but is NOT in the valid model set "
                    + "— may return HTTP 404. Falling back to default: '{}'", trimmed, defaultGeminiModel);
            return defaultGeminiModel;
        }

        // Rule 5: Ollama / non-Gemini model name stored while running on Render
        log.debug("[MODEL] resolveGeminiModel: stored='{}' is not a Gemini model — using default: '{}'",
                trimmed, defaultGeminiModel);
        return defaultGeminiModel;
    }

    private String buildGeminiPayload(User user, ChatHistory chat, double temperature, int maxTokens) throws Exception {
        ObjectNode payloadNode = objectMapper.createObjectNode();

        // 1. System Instruction
        List<Memory> memories = new ArrayList<>();
        if (user.getSettings() != null && user.getSettings().getMemoryEnabled()) {
            memories = memoryRepository.findByUserId(user.getId());
        }
        String systemPromptText = buildSystemPrompt(user, memories);

        ObjectNode systemInstructionNode = objectMapper.createObjectNode();
        ArrayNode partsArray = objectMapper.createArrayNode();
        ObjectNode textPart = objectMapper.createObjectNode();
        textPart.put("text", systemPromptText);
        partsArray.add(textPart);
        systemInstructionNode.set("parts", partsArray);
        payloadNode.set("systemInstruction", systemInstructionNode);

        // 2. Contents (Context history & Current input)
        ArrayNode contentsArray = objectMapper.createArrayNode();
        List<Message> history = chat.getMessages();

        // Filter and merge consecutive same-role messages to satisfy Gemini's alternating turns rule
        List<Message> filteredMessages = new ArrayList<>();
        String lastRole = null;
        StringBuilder mergedContent = new StringBuilder();

        for (Message msg : history) {
            String currentRole = "user".equalsIgnoreCase(msg.getRole()) ? "user" : "model";
            if (lastRole == null) {
                lastRole = currentRole;
                mergedContent.append(msg.getContent());
            } else if (currentRole.equals(lastRole)) {
                mergedContent.append("\n\n").append(msg.getContent());
            } else {
                filteredMessages.add(Message.builder().role(lastRole).content(mergedContent.toString()).build());
                lastRole = currentRole;
                mergedContent = new StringBuilder(msg.getContent());
            }
        }
        if (lastRole != null) {
            filteredMessages.add(Message.builder().role(lastRole).content(mergedContent.toString()).build());
        }

        // Limit last 20 messages for Gemini context window stability
        int startIndex = Math.max(0, filteredMessages.size() - 20);
        for (int i = startIndex; i < filteredMessages.size(); i++) {
            Message msg = filteredMessages.get(i);
            ObjectNode contentNode = objectMapper.createObjectNode();
            contentNode.put("role", msg.getRole());

            ArrayNode msgParts = objectMapper.createArrayNode();
            ObjectNode msgTextPart = objectMapper.createObjectNode();
            msgTextPart.put("text", msg.getContent());
            msgParts.add(msgTextPart);
            contentNode.set("parts", msgParts);

            contentsArray.add(contentNode);
        }
        payloadNode.set("contents", contentsArray);

        // 3. Generation Config
        ObjectNode generationConfig = objectMapper.createObjectNode();
        generationConfig.put("temperature", temperature);
        double topP = (user.getSettings() != null) ? user.getSettings().getTopP() : 0.9;
        generationConfig.put("topP", topP);
        generationConfig.put("maxOutputTokens", maxTokens);
        payloadNode.set("generationConfig", generationConfig);

        return objectMapper.writeValueAsString(payloadNode);
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
