package com.omni.gateway.core.downlink;

import com.omni.gateway.core.model.DownlinkResult;

public interface DownlinkResultPublisher {

    void publish(DownlinkResult result);
}
