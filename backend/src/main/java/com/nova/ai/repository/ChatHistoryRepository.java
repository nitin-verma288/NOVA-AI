package com.nova.ai.repository;

import com.nova.ai.entity.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, String> {
    List<ChatHistory> findByUserIdOrderByIsPinnedDescUpdatedAtDesc(String userId);
    List<ChatHistory> findByUserIdAndTitleContainingIgnoreCase(String userId, String searchTitle);
}
