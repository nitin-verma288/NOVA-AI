package com.nova.ai.service;

import com.nova.ai.entity.User;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;

public interface GeminiService {
    String getChatResponse(User user, String chatId, String userMessage);
    void streamChatResponse(User user, String chatId, String userMessage, SseEmitter emitter);
    List<String> getAvailableModels();
    String generateText(String prompt, String model, double temperature);
}
