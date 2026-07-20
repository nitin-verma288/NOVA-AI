package com.nova.ai.controller;

import com.nova.ai.entity.Plugin;
import com.nova.ai.service.PluginService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/plugin")
public class PluginController {

    private final PluginService pluginService;

    public PluginController(PluginService pluginService) {
        this.pluginService = pluginService;
    }

    @GetMapping("/all")
    public ResponseEntity<List<Plugin>> getAll() {
        return ResponseEntity.ok(pluginService.getAllPlugins());
    }

    @PutMapping("/toggle/{id}")
    public ResponseEntity<Plugin> toggle(@PathVariable String id, @RequestParam boolean enabled) {
        return ResponseEntity.ok(pluginService.togglePlugin(id, enabled));
    }
}
