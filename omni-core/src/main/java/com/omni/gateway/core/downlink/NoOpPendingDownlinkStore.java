package com.omni.gateway.core.downlink;

import com.omni.gateway.core.model.CommandEnvelope;

import java.util.Collections;
import java.util.List;

public final class NoOpPendingDownlinkStore implements PendingDownlinkStore {

    public static final NoOpPendingDownlinkStore INSTANCE = new NoOpPendingDownlinkStore();

    private NoOpPendingDownlinkStore() {
    }

    @Override
    public void enqueue(String deviceId, CommandEnvelope command) {
    }

    @Override
    public List<CommandEnvelope> drain(String deviceId, int maxItems) {
        return Collections.emptyList();
    }

    @Override
    public int pendingCount(String deviceId) {
        return 0;
    }
}
