package com.omni.gateway.bootstrap.api;

import com.omni.gateway.bootstrap.OmniGatewayProperties;
import com.omni.gateway.core.downlink.PendingDownlinkStore;
import com.omni.gateway.core.session.DistributedSessionIndex;
import com.omni.gateway.core.session.SessionRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/devices")
@ConditionalOnProperty(name = "omni.api.enabled", havingValue = "true", matchIfMissing = true)
public class DeviceRouteController {

    private final DistributedSessionIndex sessionIndex;
    private final SessionRegistry sessionRegistry;
    private final OmniGatewayProperties properties;
    private final PendingDownlinkStore pendingDownlinkStore;

    public DeviceRouteController(DistributedSessionIndex sessionIndex,
                                   SessionRegistry sessionRegistry,
                                   OmniGatewayProperties properties,
                                   PendingDownlinkStore pendingDownlinkStore) {
        this.sessionIndex = sessionIndex;
        this.sessionRegistry = sessionRegistry;
        this.properties = properties;
        this.pendingDownlinkStore = pendingDownlinkStore;
    }

    @GetMapping("/{deviceId}/route")
    public Map<String, Object> route(@PathVariable String deviceId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceId", deviceId);
        body.put("localNodeId", properties.getNodeId());
        var route = sessionIndex.lookup(deviceId);
        if (route.isPresent()) {
            var r = route.get();
            body.put("nodeId", r.nodeId());
            body.put("protocol", r.protocol());
            body.put("connectedAt", r.connectedAt().toString());
            body.put("downlinkTopic", properties.getDownlink().resolveNodeTopic(r.nodeId()));
            body.put("online", properties.getNodeId().equals(r.nodeId())
                    && sessionRegistry.get(deviceId).isPresent());
        } else {
            body.put("online", sessionRegistry.get(deviceId).isPresent());
            body.put("nodeId", null);
        }
        return body;
    }

    @GetMapping("/{deviceId}/session")
    public Map<String, Object> localSession(@PathVariable String deviceId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deviceId", deviceId);
        body.put("localNodeId", properties.getNodeId());
        Optional<?> local = sessionRegistry.get(deviceId).map(s -> Map.of(
                "channelId", s.getChannelId(),
                "protocol", s.getProtocolId() != null ? s.getProtocolId() : "",
                "port", s.getLocalPort(),
                "active", s.isActive()));
        body.put("localSession", local.orElse(null));
        body.put("pendingCount", pendingDownlinkStore.pendingCount(deviceId));
        return body;
    }
}
