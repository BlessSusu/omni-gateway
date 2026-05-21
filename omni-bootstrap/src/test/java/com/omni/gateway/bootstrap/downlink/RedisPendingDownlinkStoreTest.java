package com.omni.gateway.bootstrap.downlink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.gateway.core.model.CommandEnvelope;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class RedisPendingDownlinkStoreTest {

    @Container
    static RedisContainer redis = new RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME);

    private RedisPendingDownlinkStore store;

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redis.getHost(), redis.getFirstMappedPort());
        factory.afterPropertiesSet();
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        store = new RedisPendingDownlinkStore(template, new ObjectMapper(), 10, 3600);
    }

    @Test
    void enqueueAndDrain() {
        CommandEnvelope cmd = new CommandEnvelope();
        cmd.setMessageId("m1");
        cmd.setDeviceId("dev-1");
        cmd.setProtocol("simple-frame");
        cmd.setCommandType("setParam");
        store.enqueue("dev-1", cmd);
        assertEquals(1, store.pendingCount("dev-1"));
        var drained = store.drain("dev-1", 5);
        assertEquals(1, drained.size());
        assertEquals("m1", drained.get(0).getMessageId());
        assertEquals(0, store.pendingCount("dev-1"));
    }
}
