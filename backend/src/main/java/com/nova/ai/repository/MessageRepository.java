package com.nova.ai.repository;

import com.nova.ai.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {
    List<Message> findByChatIdOrderByCreatedAtAsc(String chatId);
}
