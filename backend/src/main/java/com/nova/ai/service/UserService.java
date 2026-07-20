package com.nova.ai.service;

import com.nova.ai.dto.*;

public interface UserService {
    AuthResponse signup(SignupRequest request);
    AuthResponse login(LoginRequest request);
    UserDto getCurrentUser(String username);
    SettingsDto getSettings(String username);
    SettingsDto updateSettings(String username, SettingsDto settingsDto);
}
