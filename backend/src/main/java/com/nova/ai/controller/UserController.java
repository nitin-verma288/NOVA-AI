package com.nova.ai.controller;

import com.nova.ai.dto.SettingsDto;
import com.nova.ai.dto.UserDto;
import com.nova.ai.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public ResponseEntity<UserDto> getProfile() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(userService.getCurrentUser(username));
    }

    @GetMapping("/settings")
    public ResponseEntity<SettingsDto> getSettings() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(userService.getSettings(username));
    }

    @PutMapping("/settings")
    public ResponseEntity<SettingsDto> updateSettings(@RequestBody SettingsDto settingsDto) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(userService.updateSettings(username, settingsDto));
    }
}
