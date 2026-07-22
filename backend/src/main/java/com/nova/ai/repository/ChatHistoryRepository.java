package com.nova.ai.repository;

import com.nova.ai.entity.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, String> {

    /**
     * Loads chat histories with messages eagerly fetched in a single JOIN query.
     * Prevents LazyInitializationException when messages are accessed outside a session.
     */
    @Query("SELECT DISTINCT c FROM ChatHistory c "
         + "LEFT JOIN FETCH c.messages m "
         + "WHERE c.user.id = :userId "
         + "ORDER BY c.isPinned DESC, c.updatedAt DESC")
    List<ChatHistory> findByUserIdWithMessages(@Param("userId") String userId);

    /**
     * Loads a single chat history with its messages eagerly fetched.
     */
    @Query("SELECT c FROM ChatHistory c LEFT JOIN FETCH c.messages WHERE c.id = :chatId")
    Optional<ChatHistory> findByIdWithMessages(@Param("chatId") String chatId);

    /**
     * Search chats by title with messages eagerly fetched.
     */
    @Query("SELECT DISTINCT c FROM ChatHistory c "
         + "LEFT JOIN FETCH c.messages m "
         + "WHERE c.user.id = :userId "
         + "AND LOWER(c.title) LIKE LOWER(CONCAT('%', :title, '%'))")
    List<ChatHistory> findByUserIdAndTitleWithMessages(
            @Param("userId") String userId,
            @Param("title") String title);

    // Plain queries (no message fetch) — kept for internal service use where session is active
    List<ChatHistory> findByUserIdOrderByIsPinnedDescUpdatedAtDesc(String userId);
    List<ChatHistory> findByUserIdAndTitleContainingIgnoreCase(String userId, String searchTitle);
}
