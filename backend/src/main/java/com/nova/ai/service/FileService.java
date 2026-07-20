package com.nova.ai.service;

import com.nova.ai.dto.DocumentDto;
import com.nova.ai.entity.User;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface FileService {
    DocumentDto uploadAndProcessFile(User user, MultipartFile file);
    List<DocumentDto> getAllDocuments(User user);
    DocumentDto getDocumentById(User user, String docId);
    void deleteDocument(User user, String docId);
    String askDocumentQuestion(User user, String docId, String question);
    String getDocumentContent(User user, String docId);
}
