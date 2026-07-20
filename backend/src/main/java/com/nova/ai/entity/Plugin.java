package com.nova.ai.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "plugins")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plugin {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private Boolean isEnabled = false;

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;
}
