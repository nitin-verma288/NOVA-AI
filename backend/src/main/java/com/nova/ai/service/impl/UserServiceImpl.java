package com.nova.ai.service.impl;

import com.nova.ai.dto.*;
import com.nova.ai.entity.Settings;
import com.nova.ai.entity.User;
import com.nova.ai.exception.BadRequestException;
import com.nova.ai.exception.ResourceNotFoundException;
import com.nova.ai.repository.SettingsRepository;
import com.nova.ai.repository.UserRepository;
import com.nova.ai.security.JwtTokenProvider;
import com.nova.ai.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final SettingsRepository settingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;

    @org.springframework.beans.factory.annotation.Value("${nova.ollama.default-model:gemma3:4b}")
    private String defaultModel;

    public UserServiceImpl(UserRepository userRepository,
                           SettingsRepository settingsRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider tokenProvider,
                           AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.settingsRepository = settingsRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.authenticationManager = authenticationManager;
    }

    @Override
    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username is already taken!");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email is already registered!");
        }

        // Create user
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        User savedUser = userRepository.save(user);

        // Create default settings for user
        Settings settings = Settings.builder()
                .user(savedUser)
                .modelName(defaultModel != null ? defaultModel : "gemma3:4b")
                .temperature(0.7)
                .topP(0.9)
                .maxTokens(2048)
                .theme("dark")
                .fontSize(14)
                .memoryEnabled(true)
                .build();

        settingsRepository.save(settings);
        savedUser.setSettings(settings);

        // Authenticate new user manually to avoid SQLite transaction locks / deadlocks
        org.springframework.security.core.userdetails.UserDetails userDetails = 
                new org.springframework.security.core.userdetails.User(
                        savedUser.getUsername(),
                        savedUser.getPassword(),
                        new java.util.ArrayList<>()
                );
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String jwt = tokenProvider.generateToken(authentication);

        return AuthResponse.builder()
                .token(jwt)
                .id(savedUser.getId())
                .username(savedUser.getUsername())
                .email(savedUser.getEmail())
                .build();
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = tokenProvider.generateToken(authentication);

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return AuthResponse.builder()
                .token(jwt)
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getCurrentUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        SettingsDto settingsDto = null;
        if (user.getSettings() != null) {
            settingsDto = mapToSettingsDto(user.getSettings());
        }

        return UserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .settings(settingsDto)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public SettingsDto getSettings(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Settings settings = settingsRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Settings not found for user: " + user.getId()));

        return mapToSettingsDto(settings);
    }

    @Override
    @Transactional
    public SettingsDto updateSettings(String username, SettingsDto settingsDto) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Settings settings = settingsRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Settings not found for user: " + user.getId()));

        if (settingsDto.getModelName() != null) settings.setModelName(settingsDto.getModelName());
        if (settingsDto.getTemperature() != null) settings.setTemperature(settingsDto.getTemperature());
        if (settingsDto.getTopP() != null) settings.setTopP(settingsDto.getTopP());
        if (settingsDto.getMaxTokens() != null) settings.setMaxTokens(settingsDto.getMaxTokens());
        if (settingsDto.getTheme() != null) settings.setTheme(settingsDto.getTheme());
        if (settingsDto.getFontSize() != null) settings.setFontSize(settingsDto.getFontSize());
        if (settingsDto.getMemoryEnabled() != null) settings.setMemoryEnabled(settingsDto.getMemoryEnabled());

        Settings updatedSettings = settingsRepository.save(settings);
        return mapToSettingsDto(updatedSettings);
    }

    private SettingsDto mapToSettingsDto(Settings settings) {
        return SettingsDto.builder()
                .modelName(settings.getModelName())
                .temperature(settings.getTemperature())
                .topP(settings.getTopP())
                .maxTokens(settings.getMaxTokens())
                .theme(settings.getTheme())
                .fontSize(settings.getFontSize())
                .memoryEnabled(settings.getMemoryEnabled())
                .build();
    }
}
