package com.omni.gateway.bootstrap.downlink;

import com.omni.gateway.core.lifecycle.DeviceOnlineListener;
import com.omni.gateway.core.session.DeviceSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "omni.downlink.pending-enabled", havingValue = "true")
public class PendingDownlinkOnlineListener implements DeviceOnlineListener {

    private final PendingDownlinkReplayService replayService;

    public PendingDownlinkOnlineListener(PendingDownlinkReplayService replayService) {
        this.replayService = replayService;
    }

    @Override
    public void onDeviceOnline(DeviceSession session) {
        replayService.onDeviceOnline(session);
    }
}
