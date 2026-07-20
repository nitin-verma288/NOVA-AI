package com.nova.ai.service;

import com.nova.ai.dto.MemoryDto;
import com.nova.ai.entity.User;
import java.util.List;

public interface MemoryService {
    List<MemoryDto> getAllMemories(User user);
    List<MemoryDto> getMemoriesByCategory(User user, String category);
    MemoryDto addMemory(User user, MemoryDto memoryDto);
    MemoryDto updateMemory(User user, String memoryId, MemoryDto memoryDto);
    void deleteMemory(User user, String memoryId);
    List<MemoryDto> searchMemories(User user, String query);
    String exportMemories(User user);
    void importMemories(User user, String importJson);
    void extractMemoriesFromConversation(User user, String chatId);
}
