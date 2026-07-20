package com.nova.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemoryDto {
    private String id;

    @NotBlank(message = "Category is required")
    private String category;

    @NotBlank(message = "Content is required")
    private String content;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
