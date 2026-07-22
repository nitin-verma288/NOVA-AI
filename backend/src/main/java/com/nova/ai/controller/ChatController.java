package com.nova.ai.controller;

import com.nova.ai.dto.ChatHistoryDto;
import com.nova.ai.dto.MessageDto;
import com.nova.ai.entity.ChatHistory;
import com.nova.ai.entity.Message;
import com.nova.ai.entity.User;
import com.nova.ai.exception.ResourceNotFoundException;
import com.nova.ai.repository.ChatHistoryRepository;
import com.nova.ai.repository.MessageRepository;
import com.nova.ai.repository.UserRepository;
import com.nova.ai.service.ChatService;
import com.nova.ai.service.MemoryService;
import com.nova.ai.service.OllamaService;
import com.nova.ai.service.PluginService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/chat")
@Slf4j
public class ChatController {

    private final UserRepository userRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final MessageRepository messageRepository;
    private final ChatService chatService;
    private final OllamaService ollamaService;
    private final PluginService pluginService;
    private final MemoryService memoryService;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public ChatController(UserRepository userRepository,
                          ChatHistoryRepository chatHistoryRepository,
                          MessageRepository messageRepository,
                          ChatService chatService,
                          OllamaService ollamaService,
                          PluginService pluginService,
                          MemoryService memoryService) {
        this.userRepository = userRepository;
        this.chatHistoryRepository = chatHistoryRepository;
        this.messageRepository = messageRepository;
        this.chatService = chatService;
        this.ollamaService = ollamaService;
        this.pluginService = pluginService;
        this.memoryService = memoryService;
    }

    @Data
    public static class MessageRequest {
        private String chatId;
        private String message;
    }

    // ---------------------------------------------------------------
    // POST /api/chat/create
    // ---------------------------------------------------------------

    @PostMapping("/create")
    public ResponseEntity<ChatHistoryDto> createChat(@RequestBody(required = false) ChatHistoryDto requestDto) {
        User user = resolveUser();
        String title = (requestDto != null && requestDto.getTitle() != null && !requestDto.getTitle().trim().isEmpty())
                ? requestDto.getTitle()
                : "New Conversation";

        // Delegate to ChatService — runs inside @Transactional
        ChatHistoryDto dto = chatService.createChat(user, title);
        return ResponseEntity.ok(dto);
    }

    // ---------------------------------------------------------------
    // GET /api/chat/history
    // ---------------------------------------------------------------

    /**
     * Returns all chat histories with their messages.
     * ChatService.getHistory() uses a JOIN FETCH query so messages are
     * loaded inside the Hibernate session — no LazyInitializationException.
     */
    @GetMapping("/history")
    public ResponseEntity<List<ChatHistoryDto>> getHistory() {
        User user = resolveUser();
        log.info("[DEBUG] ChatController: getHistory called for user={}", user.getUsername());

        // All DB access + mapping happens inside @Transactional(readOnly=true)
        List<ChatHistoryDto> dtos = chatService.getHistory(user);
        log.info("[DEBUG] ChatController: getHistory returning {} chat(s)", dtos.size());
        return ResponseEntity.ok(dtos);
    }

    // ---------------------------------------------------------------
    // GET /api/chat/search
    // ---------------------------------------------------------------

    @GetMapping("/search")
    public ResponseEntity<List<ChatHistoryDto>> searchChats(@RequestParam String title) {
        User user = resolveUser();
        List<ChatHistoryDto> dtos = chatService.searchChats(user, title);
        return ResponseEntity.ok(dtos);
    }

    // ---------------------------------------------------------------
    // PUT /api/chat/pin/{id}
    // ---------------------------------------------------------------

    @PutMapping("/pin/{id}")
    public ResponseEntity<ChatHistoryDto> togglePin(@PathVariable String id) {
        // ChatService.togglePin() loads with JOIN FETCH and maps inside @Transactional
        ChatHistoryDto dto = chatService.togglePin(id);
        return ResponseEntity.ok(dto);
    }

    // ---------------------------------------------------------------
    // DELETE /api/chat/delete/{id}
    // ---------------------------------------------------------------

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteChat(@PathVariable String id) {
        chatService.deleteChat(id);
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------
    // POST /api/chat/send  (synchronous)
    // ---------------------------------------------------------------

    @PostMapping("/send")
    public ResponseEntity<MessageDto> sendMessage(@RequestBody MessageRequest request) {
        User user = resolveUser();

        // Check plugins first
        String pluginResult = pluginService.executePluginIfTriggered(request.getMessage());
        if (pluginResult != null) {
            ChatHistory chat = chatHistoryRepository.findById(request.getChatId())
                    .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));

            Message userMsg = Message.builder().chat(chat).role("user").content(request.getMessage()).build();
            messageRepository.save(userMsg);

            Message assistantMsg = Message.builder().chat(chat).role("assistant").content(pluginResult).build();
            Message savedAssistantMsg = messageRepository.save(assistantMsg);

            chat.setUpdatedAt(LocalDateTime.now());
            chatHistoryRepository.save(chat);

            return ResponseEntity.ok(mapMessageToDto(savedAssistantMsg));
        }

        // LLM response (Ollama or Gemini via OllamaServiceImpl dispatcher)
        ollamaService.getChatResponse(user, request.getChatId(), request.getMessage());

        // Background memory extraction
        memoryService.extractMemoriesFromConversation(user, request.getChatId());

        // getLastMessage uses JOIN FETCH inside @Transactional — no lazy init issue
        MessageDto lastMsg = chatService.getLastMessage(request.getChatId());
        return ResponseEntity.ok(lastMsg);
    }

    // ---------------------------------------------------------------
    // GET /api/chat/stream  (SSE streaming)
    // ---------------------------------------------------------------

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@RequestParam String chatId, @RequestParam String message) {
        log.info("[DEBUG] ChatController: streamMessage entered. chatId={}, message='{}'", chatId, message);
        try {
            SseEmitter emitter = new SseEmitter(180000L); // 3 minutes timeout
            User user = resolveUser();
            log.info("[DEBUG] ChatController: authenticated username={}", user.getUsername());

            // 1. Check if tool plugin triggered
            String pluginResult = pluginService.executePluginIfTriggered(message);
            if (pluginResult != null) {
                log.info("[DEBUG] ChatController: plugin triggered, result length={}", pluginResult.length());
                executorService.submit(() -> {
                    try {
                        ChatHistory chat = chatHistoryRepository.findById(chatId)
                                .orElseThrow(() -> new ResourceNotFoundException("Chat not found"));

                        Message userMsg = Message.builder().chat(chat).role("user").content(message).build();
                        messageRepository.save(userMsg);

                        Message assistantMsg = Message.builder().chat(chat).role("assistant").content(pluginResult).build();
                        messageRepository.save(assistantMsg);

                        chat.setUpdatedAt(LocalDateTime.now());
                        chatHistoryRepository.save(chat);

                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        com.fasterxml.jackson.databind.node.ObjectNode chunkNode = mapper.createObjectNode();
                        chunkNode.put("content", pluginResult);
                        emitter.send(SseEmitter.event().data(mapper.writeValueAsString(chunkNode)));
                        emitter.send("[DONE]");
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("[DEBUG] ChatController: error in plugin task", e);
                        emitter.completeWithError(e);
                    }
                });
                return emitter;
            }

            // 2. Stream LLM response
            log.info("[DEBUG] ChatController: calling streamChatResponse");
            ollamaService.streamChatResponse(user, chatId, message, emitter);

            // 3. Async memory extraction
            executorService.submit(() -> {
                try {
                    Thread.sleep(5000);
                    log.info("[DEBUG] ChatController async: triggering memory extraction");
                    memoryService.extractMemoriesFromConversation(user, chatId);
                } catch (Exception e) {
                    log.error("[DEBUG] ChatController async: memory extraction failed", e);
                }
            });

            log.info("[DEBUG] ChatController: returning SseEmitter for chatId={}", chatId);
            return emitter;
        } catch (Exception e) {
            log.error("[DEBUG] ChatController: error in streamMessage", e);
            throw e;
        }
    }

    // ---------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------

    private User resolveUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    private MessageDto mapMessageToDto(Message message) {
        return MessageDto.builder()
                .id(message.getId())
                .role(message.getRole())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
