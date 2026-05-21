package com.omni.gateway.bootstrap.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.gateway.bootstrap.OmniGatewayProperties;
import com.omni.gateway.bootstrap.session.RedisSessionIndex;
import com.omni.gateway.core.session.DistributedSessionIndex;
import com.omni.gateway.core.session.NoOpDistributedSessionIndex;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class SessionIndexConfiguration {

    @Bean
    @ConditionalOnProperty(name = "omni.session.redis-enabled", havingValue = "false", matchIfMissing = true)
    public DistributedSessionIndex noOpSessionIndex() {
        return NoOpDistributedSessionIndex.INSTANCE;
    }

    @Bean
    @ConditionalOnProperty(name = "omni.session.redis-enabled", havingValue = "true")
    public DistributedSessionIndex redisSessionIndex(StringRedisTemplate redis,
                                                   ObjectMapper objectMapper,
                                                   OmniGatewayProperties properties) {
        return new RedisSessionIndex(redis, objectMapper, properties.getNodeId());
    }
}
