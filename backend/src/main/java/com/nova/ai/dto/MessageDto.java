package com.nova.ai.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageDto {
    private String id;
    private String role;
    private String content;
    private LocalDateTime createdAt;
}
