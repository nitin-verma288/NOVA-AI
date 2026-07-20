package com.nova.ai.controller;

import com.nova.ai.dto.DocumentDto;
import com.nova.ai.entity.User;
import com.nova.ai.exception.ResourceNotFoundException;
import com.nova.ai.repository.UserRepository;
import com.nova.ai.service.FileService;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/file")
public class FileController {

    private final FileService fileService;
    private final UserRepository userRepository;

    public FileController(FileService fileService, UserRepository userRepository) {
        this.fileService = fileService;
        this.userRepository = userRepository;
    }

    private User getAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Data
    public static class QueryRequest {
        private String question;
    }

    @PostMapping("/upload")
    public ResponseEntity<DocumentDto> uploadFile(@RequestParam("file") MultipartFile file) {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(fileService.uploadAndProcessFile(user, file));
    }

    @GetMapping("/all")
    public ResponseEntity<List<DocumentDto>> getAll() {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(fileService.getAllDocuments(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentDto> getById(@PathVariable String id) {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(fileService.getDocumentById(user, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        User user = getAuthenticatedUser();
        fileService.deleteDocument(user, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/query/{id}")
    public ResponseEntity<String> queryDocument(@PathVariable String id, @RequestBody QueryRequest request) {
        User user = getAuthenticatedUser();
        String answer = fileService.askDocumentQuestion(user, id, request.getQuestion());
        return ResponseEntity.ok(answer);
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<String> getContent(@PathVariable String id) {
        User user = getAuthenticatedUser();
        return ResponseEntity.ok(fileService.getDocumentContent(user, id));
    }
}
