package com.omni.gateway.core.plugin;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PluginRegistry {

    Optional<ProtocolPlugin> get(String pluginId);

    List<ProtocolPlugin> resolveForPort(int port, List<String> pluginIds);

    Collection<ProtocolPlugin> all();
}
