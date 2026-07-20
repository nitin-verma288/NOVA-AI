package com.nova.ai.service.impl;

import com.nova.ai.dto.LocalSearchResponse;
import com.nova.ai.entity.User;
import com.nova.ai.service.LocalSearchService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
public class LocalSearchServiceImpl implements LocalSearchService {

    private static final int MAX_DEPTH = 4;
    private static final int MAX_RESULTS = 50;
    private static final Set<String> EXCLUDED_DIRS = Set.of(
            "node_modules", ".git", ".idea", ".metadata", ".gradle", "target", "build", "bin", "out", "dist", ".gemini"
    );
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "json", "java", "py", "js", "ts", "html", "css", "csv", "xml", "properties", "yaml", "yml", "ini", "log", "sql"
    );

    @Override
    public List<LocalSearchResponse> searchLocal(User user, String query, String rootPath) {
        List<LocalSearchResponse> results = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return results;
        }

        String searchRoot = rootPath;
        if (searchRoot == null || searchRoot.trim().isEmpty()) {
            searchRoot = System.getProperty("user.dir"); // Defaults to active workspace directory
        }

        File rootDir = new File(searchRoot);
        if (!rootDir.exists() || !rootDir.isDirectory()) {
            log.warn("Invalid search root directory: {}", searchRoot);
            return results;
        }

        log.info("Starting local search for query: '{}' in root: '{}'", query, searchRoot);
        try {
            traverseAndSearch(rootDir, query.toLowerCase(), results, 0);
        } catch (Exception e) {
            log.error("Error during directory traversal", e);
        }
        return results;
    }

    private void traverseAndSearch(File currentFile, String queryLower, List<LocalSearchResponse> results, int depth) {
        if (results.size() >= MAX_RESULTS || depth > MAX_DEPTH) {
            return;
        }

        File[] files = currentFile.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (results.size() >= MAX_RESULTS) return;

            String name = file.getName();
            String nameLower = name.toLowerCase();

            // Exclude directories we don't want to crawl
            if (file.isDirectory()) {
                if (EXCLUDED_DIRS.contains(name)) {
                    continue;
                }

                // If folder name matches query
                if (nameLower.contains(queryLower)) {
                    results.add(buildFolderResponse(file));
                }

                // Recursively traverse
                traverseAndSearch(file, queryLower, results, depth + 1);
            } else {
                // If file name matches query
                if (nameLower.contains(queryLower)) {
                    results.add(buildFileResponse(file, "Filename Match"));
                    continue;
                }

                // Search inside content
                String ext = getFileExtension(file);
                if (TEXT_EXTENSIONS.contains(ext.toLowerCase())) {
                    searchInTextFile(file, queryLower, results);
                } else if ("pdf".equalsIgnoreCase(ext)) {
                    searchInPdfFile(file, queryLower, results);
                }
            }
        }
    }

    private void searchInTextFile(File file, String queryLower, List<LocalSearchResponse> results) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase().contains(queryLower)) {
                    String snippet = "Line " + lineNumber + ": " + line.trim();
                    results.add(buildFileResponse(file, snippet));
                    return; // Return first match inside file to avoid duplicating same file multiple times
                }
                lineNumber++;
            }
        } catch (Exception e) {
            // Ignore unreadable files silently
        }
    }

    private void searchInPdfFile(File file, String queryLower, List<LocalSearchResponse> results) {
        try (PDDocument doc = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            int idx = text.toLowerCase().indexOf(queryLower);
            if (idx != -1) {
                int start = Math.max(0, idx - 30);
                int end = Math.min(text.length(), idx + queryLower.length() + 40);
                String snippet = "..." + text.substring(start, end).replace('\n', ' ').trim() + "...";
                results.add(buildFileResponse(file, snippet));
            }
        } catch (Exception e) {
            // Ignore unreadable PDFs silently
        }
    }

    private LocalSearchResponse buildFolderResponse(File file) {
        return LocalSearchResponse.builder()
                .name(file.getName())
                .path(file.getAbsolutePath())
                .type("folder")
                .size(0L)
                .matchSnippet("Folder matches query name")
                .lastModified(formatTime(file.lastModified()))
                .build();
    }

    private LocalSearchResponse buildFileResponse(File file, String snippet) {
        return LocalSearchResponse.builder()
                .name(file.getName())
                .path(file.getAbsolutePath())
                .type(getFileExtension(file).toLowerCase())
                .size(file.length())
                .matchSnippet(snippet)
                .lastModified(formatTime(file.lastModified()))
                .build();
    }

    private String getFileExtension(File file) {
        String name = file.getName();
        int idx = name.lastIndexOf('.');
        return (idx > 0) ? name.substring(idx + 1) : "";
    }

    private String formatTime(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
