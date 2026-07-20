package com.nova.ai.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Settings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @Column(name = "model_name", nullable = false)
    @Builder.Default
    private String modelName = "gemma3:4b";

    @Column(nullable = false)
    @Builder.Default
    private Double temperature = 0.7;

    @Column(name = "top_p", nullable = false)
    @Builder.Default
    private Double topP = 0.9;

    @Column(name = "max_tokens", nullable = false)
    @Builder.Default
    private Integer maxTokens = 2048;


    @Column(nullable = false)
    @Builder.Default
    private String theme = "dark";

    @Column(name = "font_size", nullable = false)
    @Builder.Default
    private Integer fontSize = 14;

    @Column(name = "memory_enabled", nullable = false)
    @Builder.Default
    private Boolean memoryEnabled = true;
}
