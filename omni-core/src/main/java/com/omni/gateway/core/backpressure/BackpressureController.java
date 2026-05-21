package com.omni.gateway.core.backpressure;

import io.netty.channel.Channel;

/**
 * 上行 Kafka 受阻或堆积时，对 TCP Channel 施加/解除背压（autoRead）。
 */
public interface BackpressureController {

    void registerChannel(Channel channel);

    void unregisterChannel(Channel channel);

    void beforeUplinkPublish(Channel channel);

    void afterUplinkPublish(Channel channel, boolean success);

    void onKafkaDegraded();

    void onKafkaRecovered();

    boolean isUnderPressure();
}
