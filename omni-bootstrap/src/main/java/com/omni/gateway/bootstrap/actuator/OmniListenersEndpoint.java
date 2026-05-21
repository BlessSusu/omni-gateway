package com.omni.gateway.bootstrap.actuator;

import com.omni.gateway.core.session.SessionRegistry;
import com.omni.gateway.network.server.PortListenerManager;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Endpoint(id = "omnilisteners")
public class OmniListenersEndpoint {

    private final PortListenerManager portListenerManager;
    private final SessionRegistry sessionRegistry;

    public OmniListenersEndpoint(PortListenerManager portListenerManager, SessionRegistry sessionRegistry) {
        this.portListenerManager = portListenerManager;
        this.sessionRegistry = sessionRegistry;
    }

    @ReadOperation
    public Map<String, Object> read() {
        List<Map<String, Object>> ports = new ArrayList<>();
        for (int port : portListenerManager.activePorts()) {
            ports.add(Map.of(
                    "port", port,
                    "sessions", sessionRegistry.localSessionCountOnPort(port),
                    "draining", portListenerManager.isPortDraining(port)));
        }
        return Map.of(
                "acceptingConnections", portListenerManager.isAcceptingConnections(),
                "totalSessions", sessionRegistry.localSessionCount(),
                "listeners", ports);
    }
}
