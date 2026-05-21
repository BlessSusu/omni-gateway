package com.omni.gateway.bootstrap.downlink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.gateway.core.downlink.PendingDownlinkStore;
import com.omni.gateway.core.model.CommandEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class RedisPendingDownlinkStore implements PendingDownlinkStore {

    private static final Logger log = LoggerFactory.getLogger(RedisPendingDownlinkStore.class);
    private static final String KEY_PREFIX = "omni:downlink:pending:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final int maxPerDevice;
    private final long ttlSeconds;

    public RedisPendingDownlinkStore(StringRedisTemplate redis,
                                     ObjectMapper objectMapper,
                                     int maxPerDevice,
                                     long ttlSeconds) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.maxPerDevice = maxPerDevice;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public void enqueue(String deviceId, CommandEnvelope command) {
        try {
            String key = key(deviceId);
            String json = objectMapper.writeValueAsString(command);
            redis.opsForList().rightPush(key, json);
            redis.opsForList().trim(key, -maxPerDevice, -1);
            redis.expire(key, Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.warn("Pending downlink enqueue failed deviceId={}", deviceId, e);
        }
    }

    @Override
    public List<CommandEnvelope> drain(String deviceId, int maxItems) {
        List<CommandEnvelope> out = new ArrayList<>();
        try {
            String key = key(deviceId);
            for (int i = 0; i < maxItems; i++) {
                String json = redis.opsForList().leftPop(key);
                if (json == null) {
                    break;
                }
                out.add(objectMapper.readValue(json, CommandEnvelope.class));
            }
        } catch (Exception e) {
            log.warn("Pending downlink drain failed deviceId={}", deviceId, e);
        }
        return out;
    }

    @Override
    public int pendingCount(String deviceId) {
        Long size = redis.opsForList().size(key(deviceId));
        return size != null ? size.intValue() : 0;
    }

    private static String key(String deviceId) {
        return KEY_PREFIX + deviceId;
    }
}
