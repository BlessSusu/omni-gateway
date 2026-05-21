package com.omni.gateway.bootstrap;

import com.omni.gateway.network.server.PortListenerManager;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class GatewayLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GatewayLifecycle.class);

    private final PortListenerManager portListenerManager;
    private final OmniGatewayProperties properties;

    public GatewayLifecycle(PortListenerManager portListenerManager,
                            OmniGatewayProperties properties) {
        this.portListenerManager = portListenerManager;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() throws InterruptedException {
        portListenerManager.startAll();
        log.info("OmniGateway started (Phase 1 MVP), protocol-hex-enabled={}",
                properties.getLogging().isProtocolHexEnabled());
    }

    @PreDestroy
    public void onShutdown() {
        portListenerManager.stopAll();
    }
}
