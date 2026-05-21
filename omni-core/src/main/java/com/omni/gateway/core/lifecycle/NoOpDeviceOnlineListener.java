package com.omni.gateway.core.lifecycle;

import com.omni.gateway.core.session.DeviceSession;

public final class NoOpDeviceOnlineListener implements DeviceOnlineListener {

    public static final NoOpDeviceOnlineListener INSTANCE = new NoOpDeviceOnlineListener();

    private NoOpDeviceOnlineListener() {
    }

    @Override
    public void onDeviceOnline(DeviceSession session) {
    }
}
