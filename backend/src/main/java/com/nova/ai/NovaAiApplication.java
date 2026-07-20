package com.nova.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan("com.nova.ai.entity")
@EnableJpaRepositories("com.nova.ai.repository")
public class NovaAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(NovaAiApplication.class, args);
    }
}
