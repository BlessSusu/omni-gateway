package com.omni.gateway.core.uplink;

import com.omni.gateway.core.model.ThingModel;

import java.util.concurrent.CompletableFuture;

public interface UplinkPublisher {

    CompletableFuture<Void> publish(ThingModel model);
}
