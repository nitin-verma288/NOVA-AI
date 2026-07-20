package com.nova.ai.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettingsDto {
    private String modelName;
    private Double temperature;
    private Double topP;
    private Integer maxTokens;
    private String theme;
    private Integer fontSize;
    private Boolean memoryEnabled;
}
