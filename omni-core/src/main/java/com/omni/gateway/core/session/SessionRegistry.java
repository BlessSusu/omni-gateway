package com.omni.gateway.core.session;

import java.util.Collection;
import java.util.Optional;

public interface SessionRegistry {

    void bind(String deviceId, DeviceSession session);

    void unbind(String deviceId);

    void unbindIfSame(String deviceId, DeviceSession session);

    Optional<DeviceSession> get(String deviceId);

    int localSessionCount();

    int localSessionCountOnPort(int port);

    Collection<DeviceSession> localSessions();
}
