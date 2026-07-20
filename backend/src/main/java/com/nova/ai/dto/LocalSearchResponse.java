package com.nova.ai.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocalSearchResponse {
    private String name;
    private String path;
    private String type; // "file" or "folder"
    private Long size;
    private String matchSnippet; // Matching text line or snippet
    private String lastModified;
}
