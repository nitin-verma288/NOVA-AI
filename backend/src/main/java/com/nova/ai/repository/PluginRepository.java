package com.nova.ai.repository;

import com.nova.ai.entity.Plugin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PluginRepository extends JpaRepository<Plugin, String> {
    Optional<Plugin> findByName(String name);
}
