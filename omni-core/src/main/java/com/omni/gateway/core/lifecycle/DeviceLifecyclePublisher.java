package com.omni.gateway.core.lifecycle;

import com.omni.gateway.core.session.DeviceSession;

public interface DeviceLifecyclePublisher {

    void publishOnline(DeviceSession session);

    void publishOffline(DeviceSession session);
}
