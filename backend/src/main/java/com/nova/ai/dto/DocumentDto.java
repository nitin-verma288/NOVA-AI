package com.nova.ai.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentDto {
    private String id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String summary;
    private LocalDateTime createdAt;
}
