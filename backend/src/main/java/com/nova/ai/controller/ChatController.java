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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
@Slf4j
public class ChatController {

    private final ChatHistoryRepository chatHistoryRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final OllamaService ollamaService;
    private final PluginService pluginService;
    private final MemoryService memoryService;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public ChatController(ChatHistoryRepository chatHistoryRepository,
                          MessageRepository messageRepository,
                          UserRepository userRepository,
                          OllamaService ollamaService,
                          PluginService pluginService,
                          MemoryService memoryService) {
        this.chatHistoryRepository = chatHistoryRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.ollamaService = ollamaService;
        this.pluginService = pluginService;
        this.memoryService = memoryService;
    }

    @Data
    public static class MessageRequest {
        private String chatId;
        private String message;
    }

    @PostMapping("/create")
    public ResponseEntity<ChatHistoryDto> createChat(@RequestBody(required = false) ChatHistoryDto requestDto) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String title = (requestDto != null && requestDto.getTitle() != null && !requestDto.getTitle().trim().isEmpty())
                ? requestDto.getTitle()
                : "New Conversation";

        ChatHistory chat = ChatHistory.builder()
                .user(user)
                .title(title)
                .isPinned(false)
                .build();

        ChatHistory saved = chatHistoryRepository.save(chat);
        return ResponseEntity.ok(mapToDto(saved));
    }

    @GetMapping("/history")
    public ResponseEntity<List<ChatHistoryDto>> getHistory() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("[DEBUG] ChatController: getHistory called for user={}", username);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<ChatHistory> chats = chatHistoryRepository.findByUserIdOrderByIsPinnedDescUpdatedAtDesc(user.getId());
        log.info("[DEBUG] ChatController: getHistory found chats count={}", chats.size());
        for (ChatHistory chat : chats) {
            log.info("[DEBUG] ChatController: chat id={}, title={}, message count={}", 
                chat.getId(), chat.getTitle(), chat.getMessages() != null ? chat.getMessages().size() : 0);
            if (chat.getMessages() != null) {
                for (Message m : chat.getMessages()) {
                    log.info("[DEBUG] ChatController:   message id={}, role={}, contentLength={}, createdAt={}", 
                        m.getId(), m.getRole(), m.getContent() != null ? m.getContent().length() : 0, m.getCreatedAt());
                }
            }
        }
        List<ChatHistoryDto> dtos = chats.stream().map(this::mapToDto).collect(Collectors.toList());
        log.info("[DEBUG] ChatController: getHistory returning DTOs count={}", dtos.size());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/search")
    public ResponseEntity<List<ChatHistoryDto>> searchChats(@RequestParam String title) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<ChatHistory> chats = chatHistoryRepository.findByUserIdAndTitleContainingIgnoreCase(user.getId(), title);
        List<ChatHistoryDto> dtos = chats.stream().map(this::mapToDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PutMapping("/pin/{id}")
    public ResponseEntity<ChatHistoryDto> togglePin(@PathVariable String id) {
        ChatHistory chat = chatHistoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));
        chat.setIsPinned(!chat.getIsPinned());
        ChatHistory saved = chatHistoryRepository.save(chat);
        return ResponseEntity.ok(mapToDto(saved));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> deleteChat(@PathVariable String id) {
        ChatHistory chat = chatHistoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));
        chatHistoryRepository.delete(chat);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/send")
    public ResponseEntity<MessageDto> sendMessage(@RequestBody MessageRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

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

        // Standard Ollama response
        String response = ollamaService.getChatResponse(user, request.getChatId(), request.getMessage());
        
        // Background extraction
        memoryService.extractMemoriesFromConversation(user, request.getChatId());

        ChatHistory chat = chatHistoryRepository.findById(request.getChatId()).orElseThrow();
        Message lastMessage = chat.getMessages().get(chat.getMessages().size() - 1);

        return ResponseEntity.ok(mapMessageToDto(lastMessage));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@RequestParam String chatId, @RequestParam String message) {
        log.info("[DEBUG] ChatController: streamMessage endpoint entered. chatId={}, message='{}'", chatId, message);
        try {
            SseEmitter emitter = new SseEmitter(180000L); // 3 minutes timeout
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            log.info("[DEBUG] ChatController: authenticated username={}", username);
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            // 1. Check if tool plugin triggered
            String pluginResult = pluginService.executePluginIfTriggered(message);
            if (pluginResult != null) {
                log.info("[DEBUG] ChatController: plugin triggered with result length={}", pluginResult.length());
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

                        // Send plugin result wrapped in JSON
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        com.fasterxml.jackson.databind.node.ObjectNode chunkNode = mapper.createObjectNode();
                        chunkNode.put("content", pluginResult);
                        emitter.send(SseEmitter.event().data(mapper.writeValueAsString(chunkNode)));
                        emitter.send("[DONE]");
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("[DEBUG] ChatController: error in plugin execution task", e);
                        emitter.completeWithError(e);
                    }
                });
                return emitter;
            }

            // 2. Stream LLM Response
            log.info("[DEBUG] ChatController: calling streamChatResponse");
            ollamaService.streamChatResponse(user, chatId, message, emitter);

            // 3. Scan for memory asynchronously
            executorService.submit(() -> {
                try {
                    // Wait slightly for streaming to finish before scanning
                    Thread.sleep(5000);
                    log.info("[DEBUG] ChatController async: triggering memory extraction");
                    memoryService.extractMemoriesFromConversation(user, chatId);
                } catch (Exception e) {
                    log.error("[DEBUG] ChatController async: Failed to run async memory extraction", e);
                }
            });

            log.info("[DEBUG] ChatController: returning SseEmitter for chatId={}", chatId);
            return emitter;
        } catch (Exception e) {
            log.error("[DEBUG] ChatController: error in streamMessage endpoint", e);
            throw e;
        }
    }

    private ChatHistoryDto mapToDto(ChatHistory chat) {
        List<MessageDto> messageDtos = new ArrayList<>();
        if (chat.getMessages() != null) {
            messageDtos = chat.getMessages().stream()
                    .map(this::mapMessageToDto)
                    .collect(Collectors.toList());
        }

        return ChatHistoryDto.builder()
                .id(chat.getId())
                .title(chat.getTitle())
                .isPinned(chat.getIsPinned())
                .createdAt(chat.getCreatedAt())
                .updatedAt(chat.getUpdatedAt())
                .messages(messageDtos)
                .build();
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
