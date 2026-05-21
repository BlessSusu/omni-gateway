package com.omni.gateway.bootstrap.config;

import com.omni.gateway.core.plugin.PluginRegistry;
import com.omni.gateway.core.plugin.ProtocolPlugin;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class DefaultPluginRegistry implements PluginRegistry {

    private final Map<String, ProtocolPlugin> byId;

    public DefaultPluginRegistry(List<ProtocolPlugin> plugins) {
        this.byId = plugins.stream().collect(Collectors.toMap(ProtocolPlugin::pluginId, p -> p, (a, b) -> a));
    }

    @Override
    public Optional<ProtocolPlugin> get(String pluginId) {
        return Optional.ofNullable(byId.get(pluginId));
    }

    @Override
    public List<ProtocolPlugin> resolveForPort(int port, List<String> pluginIds) {
        return pluginIds.stream()
                .map(byId::get)
                .filter(p -> p != null)
                .toList();
    }

    @Override
    public Collection<ProtocolPlugin> all() {
        return byId.values();
    }
}
