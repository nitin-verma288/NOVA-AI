package com.nova.ai.controller;

import com.nova.ai.dto.MemoryDto;
import com.nova.ai.entity.User;
import com.nova.ai.exception.ResourceNotFoundException;
import com.nova.ai.repository.UserRepository;
import com.nova.ai.service.MemoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final MemoryService memoryService;
    private final UserRepository userRepository;

    public MemoryController(MemoryService memoryService, UserRepository userRepository) {
        this.memoryService = memoryService;
        this.userRepository = userRepository;
    }

    private User getAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @GetMapping("/all")
    public ResponseEntity<List<MemoryDto>> getAll() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(memoryService.getAllMemories(user));
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<MemoryDto>> getByCategory(@PathVariable String category) {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(memoryService.getMemoriesByCategory(user, category));
    }

    @PostMapping("/add")
    public ResponseEntity<MemoryDto> add(@Valid @RequestBody MemoryDto memoryDto) {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(memoryService.addMemory(user, memoryDto));
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<MemoryDto> update(@PathVariable String id, @Valid @RequestBody MemoryDto memoryDto) {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(memoryService.updateMemory(user, id, memoryDto));
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        User user = getAuthenticatedUser();
        memoryService.deleteMemory(user, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<MemoryDto>> search(@RequestParam String query) {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(memoryService.searchMemories(user, query));
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportMemories() {
        User user = getAuthenticatedUser();
        String json = memoryService.exportMemories(user);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=nova_memories.json")
                .body(json);
    }

    @PostMapping("/import")
    public ResponseEntity<Void> importMemories(@RequestBody String jsonContent) {
        User user = getAuthenticatedUser();
        memoryService.importMemories(user, jsonContent);
        return ResponseEntity.ok().build();
    }
}
