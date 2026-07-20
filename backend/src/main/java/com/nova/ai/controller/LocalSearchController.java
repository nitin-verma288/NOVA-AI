package com.nova.ai.controller;

import com.nova.ai.dto.LocalSearchRequest;
import com.nova.ai.dto.LocalSearchResponse;
import com.nova.ai.entity.User;
import com.nova.ai.exception.ResourceNotFoundException;
import com.nova.ai.repository.UserRepository;
import com.nova.ai.service.LocalSearchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class LocalSearchController {

    private final LocalSearchService localSearchService;
    private final UserRepository userRepository;

    public LocalSearchController(LocalSearchService localSearchService, UserRepository userRepository) {
        this.localSearchService = localSearchService;
        this.userRepository = userRepository;
    }

    @PostMapping("/local")
    public ResponseEntity<List<LocalSearchResponse>> searchLocal(@Valid @RequestBody LocalSearchRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<LocalSearchResponse> results = localSearchService.searchLocal(user, request.getQuery(), request.getRootPath());
        return ResponseEntity.ok(results);
    }
}
