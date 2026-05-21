package com.omni.gateway.core.lifecycle;

import com.omni.gateway.core.session.DeviceSession;

/**
 * Hook after device comes online (e.g. replay pending downlink).
 */
public interface DeviceOnlineListener {

    void onDeviceOnline(DeviceSession session);
}
