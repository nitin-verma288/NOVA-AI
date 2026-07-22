package com.nova.ai.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Set;

/**
 * On-startup data migration:
 * Scans the 'settings' table and replaces any deprecated or invalid
 * Gemini model names with the configured default (nova.gemini.default-model).
 *
 * Why: When the application runs on Render it uses Gemini. If a user's
 * stored modelName is a deprecated model (e.g. gemini-2.0-flash) or an
 * Ollama model name (e.g. gemma3:4b), the Gemini API returns HTTP 404.
 * This class ensures the DB always contains a valid Gemini model when
 * the RENDER environment variable is present.
 *
 * Safe to run every startup — it only updates rows that need fixing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiModelSanitizer {

    private final JdbcTemplate jdbcTemplate;

    @Value("${nova.gemini.default-model:gemini-3-flash-preview}")
    private String defaultGeminiModel;

    /**
     * Set of all model names that are confirmed invalid / deprecated
     * and will cause HTTP 404 from the Gemini API.
     */
    private static final Set<String> DEPRECATED_GEMINI_MODELS = Set.of(
        "gemini-2.0-flash",
        "gemini-2.0-flash-exp",
        "gemini-2.0-flash-thinking-exp",
        "gemini-3.5-flash",        // does not exist
        "gemini-1.0-pro",
        "gemini-1.0-pro-vision",
        "gemini-pro",              // legacy alias, deprecated
        "gemini-pro-vision"        // legacy alias, deprecated
    );

    /**
     * Set of currently valid / supported Gemini model identifiers.
     * Any stored value not in this set AND starting with "gemini-"
     * will also be replaced.
     */
    private static final Set<String> VALID_GEMINI_MODELS = Set.of(
        "gemini-3-flash-preview"
    );

    @PostConstruct
    public void migrateDeprecatedModels() {
        boolean isRenderEnv = System.getenv("RENDER") != null;

        if (!isRenderEnv) {
            log.debug("GeminiModelSanitizer: Not running on Render — skipping DB migration (Ollama models are valid locally).");
            return;
        }

        log.info("GeminiModelSanitizer: Running on Render — scanning settings table for deprecated/invalid model names...");
        log.info("GeminiModelSanitizer: Default Gemini model = '{}'", defaultGeminiModel);

        try {
            // Read all distinct stored model names
            List<String> storedModels = jdbcTemplate.queryForList(
                "SELECT DISTINCT model_name FROM settings WHERE model_name IS NOT NULL",
                String.class
            );

            log.info("GeminiModelSanitizer: Found {} distinct model name(s) in DB: {}", storedModels.size(), storedModels);

            int totalUpdated = 0;
            for (String stored : storedModels) {
                if (needsMigration(stored)) {
                    int rows = jdbcTemplate.update(
                        "UPDATE settings SET model_name = ? WHERE model_name = ?",
                        defaultGeminiModel, stored
                    );
                    log.warn("GeminiModelSanitizer: Migrated '{}' → '{}' for {} row(s)", stored, defaultGeminiModel, rows);
                    totalUpdated += rows;
                } else {
                    log.info("GeminiModelSanitizer: Model '{}' is valid — no migration needed", stored);
                }
            }

            // Also fix NULL model_name rows
            int nullRows = jdbcTemplate.update(
                "UPDATE settings SET model_name = ? WHERE model_name IS NULL OR TRIM(model_name) = ''",
                defaultGeminiModel
            );
            if (nullRows > 0) {
                log.warn("GeminiModelSanitizer: Fixed {} row(s) with NULL/empty model_name → '{}'", nullRows, defaultGeminiModel);
                totalUpdated += nullRows;
            }

            if (totalUpdated == 0) {
                log.info("GeminiModelSanitizer: All stored model names are valid. No migration required.");
            } else {
                log.info("GeminiModelSanitizer: Migration complete. Total rows updated: {}", totalUpdated);
            }

        } catch (Exception e) {
            // Never block application startup — just log the error
            log.error("GeminiModelSanitizer: Failed to run model migration — application will start anyway. Error: {}", e.getMessage(), e);
        }
    }

    /**
     * Returns true if the stored model name needs to be replaced.
     * A model needs migration if:
     *  - It is explicitly in the deprecated list
     *  - It starts with "gemini-" but is NOT in the valid set (unknown future-deprecated model)
     *  - It does NOT start with "gemini-" (e.g. Ollama model like "gemma3:4b" stored while on Gemini backend)
     */
    public boolean needsMigration(String modelName) {
        if (modelName == null || modelName.trim().isEmpty()) {
            return true;
        }
        String trimmed = modelName.trim();

        // Explicitly deprecated
        if (DEPRECATED_GEMINI_MODELS.contains(trimmed)) {
            return true;
        }

        // Valid Gemini model — keep as-is
        if (VALID_GEMINI_MODELS.contains(trimmed)) {
            return false;
        }

        // Unknown gemini-* model not in our valid set — treat as invalid
        if (trimmed.startsWith("gemini-")) {
            log.warn("GeminiModelSanitizer: Unknown Gemini model '{}' not in valid set — will migrate to default", trimmed);
            return true;
        }

        // Non-gemini model (Ollama model stored while running on Render/Gemini)
        // These are handled at runtime by resolveGeminiModel(), but let's migrate them too
        // to avoid confusion in logs and ensure consistent DB state
        log.info("GeminiModelSanitizer: Non-Gemini model '{}' detected in Render environment — migrating to '{}'",
                trimmed, defaultGeminiModel);
        return true;
    }
}
