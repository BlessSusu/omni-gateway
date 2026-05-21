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

    public GatewayLifecycle(PortListenerManager portListenerManager) {
        this.portListenerManager = portListenerManager;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() throws InterruptedException {
        portListenerManager.startAll();
        log.info("OmniGateway started (Phase 1 MVP)");
    }

    @PreDestroy
    public void onShutdown() {
        portListenerManager.stopAll();
    }
}
