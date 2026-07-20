package com.nova.ai.repository;

import com.nova.ai.entity.Memory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MemoryRepository extends JpaRepository<Memory, String> {
    List<Memory> findByUserId(String userId);
    List<Memory> findByUserIdAndCategory(String userId, String category);
    List<Memory> findByUserIdAndContentContainingIgnoreCase(String userId, String query);
}
