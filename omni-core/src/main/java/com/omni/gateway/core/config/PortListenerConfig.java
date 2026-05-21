package com.omni.gateway.core.config;

import java.util.ArrayList;
import java.util.List;

public class PortListenerConfig {

    private int port;
    private List<String> plugins = new ArrayList<>();
    private List<String> pluginPriority = new ArrayList<>();
    private SniffConfig sniff = new SniffConfig();

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public List<String> getPlugins() {
        return plugins;
    }

    public void setPlugins(List<String> plugins) {
        this.plugins = plugins;
    }

    public List<String> getPluginPriority() {
        return pluginPriority.isEmpty() ? plugins : pluginPriority;
    }

    public void setPluginPriority(List<String> pluginPriority) {
        this.pluginPriority = pluginPriority;
    }

    public SniffConfig getSniff() {
        return sniff;
    }

    public void setSniff(SniffConfig sniff) {
        this.sniff = sniff;
    }
}
