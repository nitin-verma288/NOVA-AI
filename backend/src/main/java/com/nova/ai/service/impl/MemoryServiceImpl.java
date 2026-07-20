package com.nova.ai.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nova.ai.dto.MemoryDto;
import com.nova.ai.entity.ChatHistory;
import com.nova.ai.entity.Message;
import com.nova.ai.entity.Memory;
import com.nova.ai.entity.User;
import com.nova.ai.exception.BadRequestException;
import com.nova.ai.exception.ResourceNotFoundException;
import com.nova.ai.repository.ChatHistoryRepository;
import com.nova.ai.repository.MemoryRepository;
import com.nova.ai.service.MemoryService;
import com.nova.ai.service.OllamaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MemoryServiceImpl implements MemoryService {

    private final MemoryRepository memoryRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final OllamaService ollamaService;
    private final ObjectMapper objectMapper;

    public MemoryServiceImpl(MemoryRepository memoryRepository,
                             ChatHistoryRepository chatHistoryRepository,
                             @Lazy OllamaService ollamaService,
                             ObjectMapper objectMapper) {
        this.memoryRepository = memoryRepository;
        this.chatHistoryRepository = chatHistoryRepository;
        this.ollamaService = ollamaService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemoryDto> getAllMemories(User user) {
        return memoryRepository.findByUserId(user.getId()).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemoryDto> getMemoriesByCategory(User user, String category) {
        return memoryRepository.findByUserIdAndCategory(user.getId(), category.toLowerCase()).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public MemoryDto addMemory(User user, MemoryDto memoryDto) {
        Memory memory = Memory.builder()
                .user(user)
                .category(memoryDto.getCategory().toLowerCase())
                .content(memoryDto.getContent())
                .build();

        Memory saved = memoryRepository.save(memory);
        return mapToDto(saved);
    }

    @Override
    @Transactional
    public MemoryDto updateMemory(User user, String memoryId, MemoryDto memoryDto) {
        Memory memory = memoryRepository.findById(memoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Memory not found"));

        if (!memory.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Unauthorized access to memory");
        }

        memory.setCategory(memoryDto.getCategory().toLowerCase());
        memory.setContent(memoryDto.getContent());

        Memory saved = memoryRepository.save(memory);
        return mapToDto(saved);
    }

    @Override
    @Transactional
    public void deleteMemory(User user, String memoryId) {
        Memory memory = memoryRepository.findById(memoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Memory not found"));

        if (!memory.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Unauthorized access to memory");
        }

        memoryRepository.delete(memory);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemoryDto> searchMemories(User user, String query) {
        return memoryRepository.findByUserIdAndContentContainingIgnoreCase(user.getId(), query).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public String exportMemories(User user) {
        try {
            List<MemoryDto> memories = getAllMemories(user);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(memories);
        } catch (Exception e) {
            log.error("Failed to export memories", e);
            throw new RuntimeException("Export failed: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void importMemories(User user, String importJson) {
        try {
            List<MemoryDto> imported = objectMapper.readValue(importJson, new TypeReference<List<MemoryDto>>() {});
            List<Memory> existing = memoryRepository.findByUserId(user.getId());

            for (MemoryDto dto : imported) {
                // Deduplicate
                boolean duplicate = existing.stream().anyMatch(e -> 
                    e.getCategory().equalsIgnoreCase(dto.getCategory()) && 
                    e.getContent().equalsIgnoreCase(dto.getContent())
                );

                if (!duplicate) {
                    Memory memory = Memory.builder()
                            .user(user)
                            .category(dto.getCategory().toLowerCase())
                            .content(dto.getContent())
                            .build();
                    memoryRepository.save(memory);
                }
            }
        } catch (Exception e) {
            log.error("Failed to import memories", e);
            throw new BadRequestException("Invalid memory import JSON data format");
        }
    }

    @Override
    @Async
    @Transactional
    public void extractMemoriesFromConversation(User user, String chatId) {
        if (user.getSettings() != null && !user.getSettings().getMemoryEnabled()) {
            return;
        }

        try {
            ChatHistory chat = chatHistoryRepository.findById(chatId)
                    .orElseThrow(() -> new ResourceNotFoundException("Chat history not found"));

            List<Message> messages = chat.getMessages();
            if (messages.size() < 2) return;

            // Take the last 4 messages to inspect
            int start = Math.max(0, messages.size() - 4);
            StringBuilder conversationSnippet = new StringBuilder();
            for (int i = start; i < messages.size(); i++) {
                Message msg = messages.get(i);
                conversationSnippet.append("[").append(msg.getRole().toUpperCase()).append("]: ")
                        .append(msg.getContent()).append("\n");
            }

            String extractionPrompt = """
                    You are a background database analyzer for NOVA AI.
                    Analyze the following recent conversation snippet between the USER and the AI:
                    
                    """ + conversationSnippet + """
                    
                    Extract any facts about the user (e.g. name, age, preferred coding style, favorite programming languages, active projects, goals, preferences) that should be remembered to personalize future conversations.
                    
                    Return ONLY a JSON array of objects representing the facts. Use categories: 'profile', 'preference', 'skill', 'project', 'goal'.
                    Example output format:
                    [
                      {"category": "profile", "content": "User's name is Nitin"},
                      {"category": "preference", "content": "Prefers React with Tailwind CSS"}
                    ]
                    
                    CRITICAL: If there are no new facts, return exactly: []
                    Do not add any explanation, markdown formatting besides standard JSON, or pre-text. Return only the JSON array.
                    """;

            String model = user.getSettings() != null ? user.getSettings().getModelName() : "gemma3:4b";
            String rawResponse = ollamaService.generateText(extractionPrompt, model, 0.0);

            // Clean response
            String jsonStr = cleanJson(rawResponse);

            if (jsonStr.startsWith("[") && jsonStr.endsWith("]")) {
                JsonNode arrayNode = objectMapper.readTree(jsonStr);
                List<Memory> currentMemories = memoryRepository.findByUserId(user.getId());

                for (JsonNode item : arrayNode) {
                    if (item.has("category") && item.has("content")) {
                        String category = item.get("category").asText().toLowerCase();
                        String content = item.get("content").asText();

                        if (content.trim().isEmpty()) continue;

                        // Check duplicate
                        boolean duplicate = currentMemories.stream().anyMatch(m -> 
                            m.getCategory().equalsIgnoreCase(category) && 
                            m.getContent().equalsIgnoreCase(content)
                        );

                        if (!duplicate) {
                            Memory newMem = Memory.builder()
                                    .user(user)
                                    .category(category)
                                    .content(content)
                                    .build();
                            memoryRepository.save(newMem);
                            log.info("Extracted and saved new memory for user: {}", content);
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("Failed to automatically extract memories from conversation", e);
        }
    }

    private String cleanJson(String raw) {
        String cleaned = raw.trim();
        // Remove code block wraps
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    private MemoryDto mapToDto(Memory memory) {
        return MemoryDto.builder()
                .id(memory.getId())
                .category(memory.getCategory())
                .content(memory.getContent())
                .createdAt(memory.getCreatedAt())
                .updatedAt(memory.getUpdatedAt())
                .build();
    }
}
