package com.omni.gateway.bootstrap.config;

import com.omni.gateway.bootstrap.downlink.PendingDownlinkOnlineListener;
import com.omni.gateway.core.lifecycle.DeviceOnlineListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class DeviceOnlineListenerConfiguration {

    @Bean
    @Primary
    public DeviceOnlineListener deviceOnlineListener(ObjectProvider<PendingDownlinkOnlineListener> pendingListener) {
        return session -> pendingListener.ifAvailable(l -> l.onDeviceOnline(session));
    }
}
