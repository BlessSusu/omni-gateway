package com.omni.gateway.core.session;

import java.time.Instant;

public record SessionRoute(String deviceId, String nodeId, String protocol, Instant connectedAt) {
}
