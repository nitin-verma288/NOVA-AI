package com.nova.ai.service.impl;

import com.nova.ai.entity.User;
import com.nova.ai.provider.AiProvider;
import com.nova.ai.service.GeminiService;
import com.nova.ai.service.OllamaLocalService;
import com.nova.ai.service.OllamaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Service
@Primary
@Slf4j
public class OllamaServiceImpl implements OllamaService {

    private final OllamaLocalService ollamaLocalService;
    private final GeminiService geminiService;

    public OllamaServiceImpl(OllamaLocalService ollamaLocalService, GeminiService geminiService) {
        this.ollamaLocalService = ollamaLocalService;
        this.geminiService = geminiService;
    }

    public AiProvider getActiveProvider() {
        // 1. If running on Render, directly use Gemini (never attempt localhost)
        if (System.getenv("RENDER") != null || "true".equalsIgnoreCase(System.getenv("IS_RENDER"))) {
            log.info("Provider selected: Gemini (Render deployment)");
            return AiProvider.GEMINI;
        }

        // 2. Otherwise check if Ollama is running
        if (ollamaLocalService.isOllamaRunning()) {
            log.debug("Provider selected: Ollama (Ollama service reachable)");
            return AiProvider.OLLAMA;
        }

        // 3. Fallback to Gemini if API key is present
        String geminiApiKey = System.getenv("GEMINI_API_KEY");
        if (geminiApiKey != null && !geminiApiKey.trim().isEmpty()) {
            log.info("Provider selected: Gemini (Ollama unreachable, Gemini key present)");
            return AiProvider.GEMINI;
        }

        // 4. Default fallback to Ollama
        log.warn("Provider selected: Ollama (Neither Ollama running nor Gemini configured)");
        return AiProvider.OLLAMA;
    }

    @Override
    public String getChatResponse(User user, String chatId, String userMessage) {
        AiProvider provider = getActiveProvider();
        if (provider == AiProvider.GEMINI) {
            log.info("Gemini request started");
            String response = geminiService.getChatResponse(user, chatId, userMessage);
            log.info("Gemini request completed");
            return response;
        } else {
            return ollamaLocalService.getChatResponse(user, chatId, userMessage);
        }
    }

    @Override
    public void streamChatResponse(User user, String chatId, String userMessage, SseEmitter emitter) {
        AiProvider provider = getActiveProvider();
        if (provider == AiProvider.GEMINI) {
            log.info("Gemini request started");
            geminiService.streamChatResponse(user, chatId, userMessage, emitter);
            log.info("Gemini request completed");
        } else {
            ollamaLocalService.streamChatResponse(user, chatId, userMessage, emitter);
        }
    }

    @Override
    public List<String> getAvailableModels() {
        AiProvider provider = getActiveProvider();
        if (provider == AiProvider.GEMINI) {
            return geminiService.getAvailableModels();
        } else {
            return ollamaLocalService.getAvailableModels();
        }
    }

    @Override
    public String generateText(String prompt, String model, double temperature) {
        AiProvider provider = getActiveProvider();
        if (provider == AiProvider.GEMINI) {
            return geminiService.generateText(prompt, model, temperature);
        } else {
            return ollamaLocalService.generateText(prompt, model, temperature);
        }
    }

    @Override
    public boolean isOllamaRunning() {
        if (System.getenv("RENDER") != null || "true".equalsIgnoreCase(System.getenv("IS_RENDER"))) {
            return false;
        }
        return ollamaLocalService.isOllamaRunning();
    }
}
