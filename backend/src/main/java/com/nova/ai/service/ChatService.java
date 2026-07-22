package com.nova.ai.service;

import com.nova.ai.dto.ChatHistoryDto;
import com.nova.ai.dto.MessageDto;
import com.nova.ai.entity.User;

import java.util.List;

/**
 * Service layer for chat operations.
 * All methods run within a Hibernate session (@Transactional),
 * ensuring lazy collections (ChatHistory.messages) are fully
 * initialized before the method returns — no LazyInitializationException.
 */
public interface ChatService {

    /** Create a new chat session and return its DTO (messages will be empty). */
    ChatHistoryDto createChat(User user, String title);

    /**
     * Return all chat histories for a user, with messages eagerly loaded
     * via JOIN FETCH — safe to use outside any session after this returns.
     */
    List<ChatHistoryDto> getHistory(User user);

    /**
     * Search chat histories by title with messages eagerly loaded.
     */
    List<ChatHistoryDto> searchChats(User user, String title);

    /** Toggle the pinned state of a chat session. */
    ChatHistoryDto togglePin(String chatId);

    /** Delete a chat session by ID. */
    void deleteChat(String chatId);

    /**
     * Return the last message of a chat as DTO — used by the synchronous
     * /send endpoint after the LLM has saved its response.
     */
    MessageDto getLastMessage(String chatId);
}
