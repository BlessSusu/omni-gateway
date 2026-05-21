package com.omni.gateway.core.downlink;

import com.omni.gateway.core.model.CommandEnvelope;

import java.util.List;

/**
 * Queue for downlink commands when device is offline (Phase 3 M20).
 */
public interface PendingDownlinkStore {

    void enqueue(String deviceId, CommandEnvelope command);

    List<CommandEnvelope> drain(String deviceId, int maxItems);

    int pendingCount(String deviceId);
}
