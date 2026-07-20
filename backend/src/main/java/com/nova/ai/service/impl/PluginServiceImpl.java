package com.nova.ai.service.impl;

import com.nova.ai.entity.Plugin;
import com.nova.ai.exception.ResourceNotFoundException;
import com.nova.ai.repository.PluginRepository;
import com.nova.ai.service.PluginService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class PluginServiceImpl implements PluginService {

    private final PluginRepository pluginRepository;

    public PluginServiceImpl(PluginRepository pluginRepository) {
        this.pluginRepository = pluginRepository;
    }

    @PostConstruct
    public void initPlugins() {
        seedPlugin("Calculator", "Evaluates basic math expressions dynamically.", true);
        seedPlugin("Weather", "Retrieves local atmospheric conditions (Offline Mock).", false);
        seedPlugin("Browser", "Simulates offline web searching and context parsing.", false);
        seedPlugin("Automation", "Launches local applications (e.g. open notepad, open calculator).", true);
        seedPlugin("Email", "Simulates composing and dispatching emails.", false);
    }

    private void seedPlugin(String name, String description, boolean defaultEnabled) {
        if (pluginRepository.findByName(name).isEmpty()) {
            Plugin plugin = Plugin.builder()
                    .name(name)
                    .description(description)
                    .isEnabled(defaultEnabled)
                    .build();
            pluginRepository.save(plugin);
            log.info("Seeded default plugin: {}", name);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Plugin> getAllPlugins() {
        return pluginRepository.findAll();
    }

    @Override
    @Transactional
    public Plugin togglePlugin(String pluginId, boolean enabled) {
        Plugin plugin = pluginRepository.findById(pluginId)
                .orElseThrow(() -> new ResourceNotFoundException("Plugin not found"));
        plugin.setIsEnabled(enabled);
        return pluginRepository.save(plugin);
    }

    @Override
    @Transactional(readOnly = true)
    public String executePluginIfTriggered(String prompt) {
        String cleanedPrompt = prompt.trim();
        List<Plugin> activePlugins = pluginRepository.findAll().stream()
                .filter(Plugin::getIsEnabled)
                .toList();

        for (Plugin plugin : activePlugins) {
            String name = plugin.getName();
            if ("Calculator".equalsIgnoreCase(name)) {
                String mathResult = tryCalculator(cleanedPrompt);
                if (mathResult != null) return mathResult;
            } else if ("Weather".equalsIgnoreCase(name)) {
                String weatherResult = tryWeather(cleanedPrompt);
                if (weatherResult != null) return weatherResult;
            } else if ("Browser".equalsIgnoreCase(name)) {
                String browserResult = tryBrowser(cleanedPrompt);
                if (browserResult != null) return browserResult;
            } else if ("Automation".equalsIgnoreCase(name)) {
                String automationResult = tryAutomation(cleanedPrompt);
                if (automationResult != null) return automationResult;
            } else if ("Email".equalsIgnoreCase(name)) {
                String emailResult = tryEmail(cleanedPrompt);
                if (emailResult != null) return emailResult;
            }
        }
        return null;
    }

    private String tryCalculator(String prompt) {
        // Matches equations like: 25 * 4, 100 / 5, 20 + 30, 50 - 12
        Pattern pattern = Pattern.compile(".*?(\\d+)\\s*([\\+\\-\\*\\/])\\s*(\\d+).*?");
        Matcher matcher = pattern.matcher(prompt);
        if (matcher.matches()) {
            try {
                double num1 = Double.parseDouble(matcher.group(1));
                String op = matcher.group(2);
                double num2 = Double.parseDouble(matcher.group(3));
                double result = 0;

                switch (op) {
                    case "+": result = num1 + num2; break;
                    case "-": result = num1 - num2; break;
                    case "*": result = num1 * num2; break;
                    case "/": 
                        if (num2 == 0) return "[Plugin: Calculator] Error: Division by zero is undefined.";
                        result = num1 / num2; 
                        break;
                }
                return String.format("[Plugin: Calculator] Calculated: %.2f %s %.2f = %.2f", num1, op, num2, result);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private String tryWeather(String prompt) {
        if (prompt.toLowerCase().contains("weather")) {
            Pattern pattern = Pattern.compile(".*?weather\\s+(?:in|for)?\\s*([a-zA-Z\\s]+).*?", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(prompt);
            String city = "your area";
            if (matcher.matches()) {
                city = matcher.group(1).trim();
            }
            return String.format("[Plugin: Weather] Current conditions in %s: 24°C, Clear Sky, Humidity: 60%%. Wind: 12 km/h. (Offline Mode)", city);
        }
        return null;
    }

    private String tryBrowser(String prompt) {
        String lower = prompt.toLowerCase();
        if (lower.contains("search web for") || lower.contains("google search") || lower.contains("browser search")) {
            String query = prompt;
            if (lower.contains("search web for")) {
                query = prompt.substring(lower.indexOf("search web for") + 15).trim();
            } else if (lower.contains("google search")) {
                query = prompt.substring(lower.indexOf("google search") + 14).trim();
            }
            return String.format("[Plugin: Browser] Simulating web indexing for query '%s'. Top match: Offline database loaded (3 mock articles read).", query);
        }
        return null;
    }

    private String tryAutomation(String prompt) {
        String lower = prompt.toLowerCase();
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");

        if (lower.contains("open notepad") || lower.contains("launch notepad")) {
            if (isWindows) {
                try {
                    Runtime.getRuntime().exec("notepad.exe");
                    return "[Plugin: Automation] Notepad application launched successfully on host system.";
                } catch (Exception e) {
                    return "[Plugin: Automation] Failed to start notepad: " + e.getMessage();
                }
            } else {
                return "[Plugin: Automation] Notepad launcher requires Windows environment.";
            }
        } else if (lower.contains("open calculator") || lower.contains("launch calculator") || lower.contains("calc app")) {
            if (isWindows) {
                try {
                    Runtime.getRuntime().exec("calc.exe");
                    return "[Plugin: Automation] Windows Calculator launched successfully on host system.";
                } catch (Exception e) {
                    return "[Plugin: Automation] Failed to start calculator: " + e.getMessage();
                }
            } else {
                return "[Plugin: Automation] Calculator launcher requires Windows environment.";
            }
        }
        return null;
    }

    private String tryEmail(String prompt) {
        String lower = prompt.toLowerCase();
        if (lower.contains("send email") || lower.contains("compose mail")) {
            Pattern pattern = Pattern.compile(".*?email\\s+(?:to)?\\s*([a-zA-Z0-9\\.\\_\\-]+@[a-zA-Z0-9\\.\\-]+\\.[a-zA-Z]{2,}).*?", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(prompt);
            String recipient = "recipient@example.com";
            if (matcher.matches()) {
                recipient = matcher.group(1).trim();
            }
            return String.format("[Plugin: Email] Draft email created for '%s'. Placed in offline outgoing queue.", recipient);
        }
        return null;
    }
}
