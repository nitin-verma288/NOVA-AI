package com.nova.ai.service;

import com.nova.ai.entity.Plugin;
import java.util.List;

public interface PluginService {
    List<Plugin> getAllPlugins();
    Plugin togglePlugin(String pluginId, boolean enabled);
    String executePluginIfTriggered(String prompt);
}
