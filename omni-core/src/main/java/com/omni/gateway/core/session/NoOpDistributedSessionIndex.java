package com.omni.gateway.core.session;

import java.util.Optional;

public final class NoOpDistributedSessionIndex implements DistributedSessionIndex {

    public static final NoOpDistributedSessionIndex INSTANCE = new NoOpDistributedSessionIndex();

    private NoOpDistributedSessionIndex() {
    }

    @Override
    public void register(String deviceId, String nodeId, String protocol, long ttlSec) {
    }

    @Override
    public void renew(String deviceId, long ttlSec) {
    }

    @Override
    public void unregister(String deviceId, String nodeId) {
    }

    @Override
    public Optional<SessionRoute> lookup(String deviceId) {
        return Optional.empty();
    }
}
