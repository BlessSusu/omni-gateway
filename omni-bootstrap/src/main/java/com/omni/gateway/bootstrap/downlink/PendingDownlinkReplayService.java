package com.omni.gateway.bootstrap.downlink;

import com.omni.gateway.bootstrap.OmniGatewayProperties;
import com.omni.gateway.core.downlink.PendingDownlinkStore;
import com.omni.gateway.core.model.CommandEnvelope;
import com.omni.gateway.core.session.DeviceSession;
import com.omni.gateway.core.session.SessionRegistry;
import com.omni.gateway.network.downlink.DeviceSerialExecutor;
import com.omni.gateway.network.downlink.DownlinkDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PendingDownlinkReplayService {

    private static final Logger log = LoggerFactory.getLogger(PendingDownlinkReplayService.class);

    private final PendingDownlinkStore pendingStore;
    private final SessionRegistry sessionRegistry;
    private final DownlinkDispatcher dispatcher;
    private final DeviceSerialExecutor serialExecutor;
    private final OmniGatewayProperties properties;

    public PendingDownlinkReplayService(PendingDownlinkStore pendingStore,
                                        SessionRegistry sessionRegistry,
                                        DownlinkDispatcher dispatcher,
                                        DeviceSerialExecutor serialExecutor,
                                        OmniGatewayProperties properties) {
        this.pendingStore = pendingStore;
        this.sessionRegistry = sessionRegistry;
        this.dispatcher = dispatcher;
        this.serialExecutor = serialExecutor;
        this.properties = properties;
    }

    public void onDeviceOnline(DeviceSession session) {
        if (!properties.getDownlink().isPendingEnabled()) {
            return;
        }
        String deviceId = session.getDeviceId();
        if (deviceId == null) {
            return;
        }
        int max = properties.getDownlink().getPendingReplayMax();
        List<CommandEnvelope> pending = pendingStore.drain(deviceId, max);
        if (pending.isEmpty()) {
            return;
        }
        log.info("Replaying {} pending downlink(s) for deviceId={}", pending.size(), deviceId);
        for (CommandEnvelope cmd : pending) {
            serialExecutor.execute(deviceId, () -> {
                sessionRegistry.get(deviceId).ifPresent(s -> dispatcher.dispatch(s, cmd));
            });
        }
    }

    public void enqueueIfOffline(CommandEnvelope cmd) {
        if (!properties.getDownlink().isPendingEnabled()) {
            return;
        }
        pendingStore.enqueue(cmd.getDeviceId(), cmd);
    }
}
