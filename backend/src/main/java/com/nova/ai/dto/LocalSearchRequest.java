package com.nova.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocalSearchRequest {
    @NotBlank(message = "Search query is required")
    private String query;

    private String rootPath; // Optional path directory on local system. If empty, uses default workspace.
}
