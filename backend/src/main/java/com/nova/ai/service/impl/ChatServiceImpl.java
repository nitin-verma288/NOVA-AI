package com.nova.ai.service.impl;

import com.nova.ai.dto.ChatHistoryDto;
import com.nova.ai.dto.MessageDto;
import com.nova.ai.entity.ChatHistory;
import com.nova.ai.entity.Message;
import com.nova.ai.entity.User;
import com.nova.ai.exception.ResourceNotFoundException;
import com.nova.ai.repository.ChatHistoryRepository;
import com.nova.ai.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Transactional service for all chat-related DB operations.
 *
 * Every public method is annotated with @Transactional, which ensures a
 * Hibernate session remains open for the duration of the method body.
 * This allows ChatHistory.messages (LAZY) to be accessed and mapped to
 * DTOs without triggering a LazyInitializationException.
 *
 * JOIN FETCH queries in ChatHistoryRepository are used for history and
 * search to load messages in a single SQL round-trip (no N+1 problem).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final ChatHistoryRepository chatHistoryRepository;

    // ---------------------------------------------------------------
    // CREATE
    // ---------------------------------------------------------------

    @Override
    @Transactional
    public ChatHistoryDto createChat(User user, String title) {
        ChatHistory chat = ChatHistory.builder()
                .user(user)
                .title(title)
                .isPinned(false)
                .build();
        ChatHistory saved = chatHistoryRepository.save(chat);
        // messages list is empty on creation; no lazy load needed
        return mapToDto(saved);
    }

    // ---------------------------------------------------------------
    // READ — history
    // ---------------------------------------------------------------

    /**
     * Uses a JOIN FETCH query to load chat + messages in one DB round-trip,
     * then maps to DTOs while still inside the transaction.
     */
    @Override
    @Transactional(readOnly = true)
    public List<ChatHistoryDto> getHistory(User user) {
        log.debug("ChatService: loading chat history for user={}", user.getUsername());

        // JOIN FETCH: messages are fully loaded inside this transaction
        List<ChatHistory> chats = chatHistoryRepository.findByUserIdWithMessages(user.getId());

        log.debug("ChatService: found {} chat(s) for user={}", chats.size(), user.getUsername());

        // Sort: pinned first, then by updatedAt desc (JOIN FETCH loses ORDER BY in some DBs)
        chats.sort(Comparator
                .comparing(ChatHistory::getIsPinned, Comparator.reverseOrder())
                .thenComparing(ChatHistory::getUpdatedAt, Comparator.reverseOrder()));

        return chats.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // READ — search
    // ---------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<ChatHistoryDto> searchChats(User user, String title) {
        List<ChatHistory> chats = chatHistoryRepository
                .findByUserIdAndTitleWithMessages(user.getId(), title);
        return chats.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    // UPDATE — pin toggle
    // ---------------------------------------------------------------

    @Override
    @Transactional
    public ChatHistoryDto togglePin(String chatId) {
        // JOIN FETCH so messages are available for mapToDto
        ChatHistory chat = chatHistoryRepository.findByIdWithMessages(chatId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found: " + chatId));
        chat.setIsPinned(!chat.getIsPinned());
        ChatHistory saved = chatHistoryRepository.save(chat);
        return mapToDto(saved);
    }

    // ---------------------------------------------------------------
    // DELETE
    // ---------------------------------------------------------------

    @Override
    @Transactional
    public void deleteChat(String chatId) {
        ChatHistory chat = chatHistoryRepository.findById(chatId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found: " + chatId));
        chatHistoryRepository.delete(chat);
    }

    // ---------------------------------------------------------------
    // HELPER — used by /send after LLM saves the response
    // ---------------------------------------------------------------

    /**
     * Loads the chat with messages (JOIN FETCH) and returns the last message as DTO.
     * Called after the LLM service has already persisted the assistant reply.
     */
    @Override
    @Transactional(readOnly = true)
    public MessageDto getLastMessage(String chatId) {
        ChatHistory chat = chatHistoryRepository.findByIdWithMessages(chatId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found: " + chatId));

        List<Message> msgs = chat.getMessages();
        if (msgs == null || msgs.isEmpty()) {
            throw new ResourceNotFoundException("No messages found in chat: " + chatId);
        }
        Message last = msgs.get(msgs.size() - 1);
        return mapMessageToDto(last);
    }

    // ---------------------------------------------------------------
    // Mapping helpers (pure data transformation, no DB access)
    // ---------------------------------------------------------------

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
