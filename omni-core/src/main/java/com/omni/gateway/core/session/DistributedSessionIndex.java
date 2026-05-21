package com.omni.gateway.core.session;

import java.util.Optional;

/**
 * Cross-node routing index (e.g. Redis). Local {@link SessionRegistry} remains the data plane.
 */
public interface DistributedSessionIndex {

    void register(String deviceId, String nodeId, String protocol, long ttlSec);

    void renew(String deviceId, long ttlSec);

    void unregister(String deviceId, String nodeId);

    Optional<SessionRoute> lookup(String deviceId);
}
