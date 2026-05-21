package com.omni.gateway.bootstrap.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.gateway.bootstrap.OmniGatewayProperties;
import com.omni.gateway.bootstrap.downlink.RedisPendingDownlinkStore;
import com.omni.gateway.core.downlink.NoOpPendingDownlinkStore;
import com.omni.gateway.core.downlink.PendingDownlinkStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class PendingDownlinkConfiguration {

    @Bean
    @ConditionalOnProperty(name = "omni.downlink.pending-enabled", havingValue = "false", matchIfMissing = true)
    public PendingDownlinkStore noOpPendingDownlinkStore() {
        return NoOpPendingDownlinkStore.INSTANCE;
    }

    @Bean
    @ConditionalOnProperty(name = {"omni.downlink.pending-enabled", "omni.session.redis-enabled"}, havingValue = "true")
    public PendingDownlinkStore redisPendingDownlinkStore(StringRedisTemplate redis,
                                                         ObjectMapper objectMapper,
                                                         OmniGatewayProperties properties) {
        return new RedisPendingDownlinkStore(
                redis,
                objectMapper,
                properties.getDownlink().getPendingMaxPerDevice(),
                properties.getDownlink().getPendingTtlSeconds());
    }
}
