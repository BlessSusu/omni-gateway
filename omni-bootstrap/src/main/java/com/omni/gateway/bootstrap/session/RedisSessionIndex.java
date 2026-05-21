package com.omni.gateway.bootstrap.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.gateway.core.session.DistributedSessionIndex;
import com.omni.gateway.core.session.SessionRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class RedisSessionIndex implements DistributedSessionIndex {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionIndex.class);
    private static final String KEY_PREFIX = "omni:session:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final String localNodeId;

    public RedisSessionIndex(StringRedisTemplate redis, ObjectMapper objectMapper, String localNodeId) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.localNodeId = localNodeId;
    }

    @Override
    public void register(String deviceId, String nodeId, String protocol, long ttlSec) {
        try {
            String key = key(deviceId);
            String json = objectMapper.writeValueAsString(new StoredRoute(nodeId, protocol, Instant.now().toEpochMilli()));
            redis.opsForValue().set(key, json, Duration.ofSeconds(ttlSec));
        } catch (Exception e) {
            log.warn("Redis session register failed deviceId={}", deviceId, e);
        }
    }

    @Override
    public void renew(String deviceId, long ttlSec) {
        try {
            String key = key(deviceId);
            Boolean ok = redis.expire(key, Duration.ofSeconds(ttlSec));
            if (Boolean.FALSE.equals(ok)) {
                log.debug("Redis session renew miss deviceId={}", deviceId);
            }
        } catch (Exception e) {
            log.warn("Redis session renew failed deviceId={}", deviceId, e);
        }
    }

    @Override
    public void unregister(String deviceId, String nodeId) {
        try {
            String key = key(deviceId);
            String current = redis.opsForValue().get(key);
            if (current == null) {
                return;
            }
            StoredRoute route = objectMapper.readValue(current, StoredRoute.class);
            if (nodeId != null && nodeId.equals(route.nodeId())) {
                redis.delete(key);
            }
        } catch (Exception e) {
            log.warn("Redis session unregister failed deviceId={}", deviceId, e);
        }
    }

    @Override
    public Optional<SessionRoute> lookup(String deviceId) {
        try {
            String json = redis.opsForValue().get(key(deviceId));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            StoredRoute stored = objectMapper.readValue(json, StoredRoute.class);
            return Optional.of(new SessionRoute(
                    deviceId,
                    stored.nodeId(),
                    stored.protocol(),
                    Instant.ofEpochMilli(stored.connectedAt())));
        } catch (Exception e) {
            log.warn("Redis session lookup failed deviceId={}", deviceId, e);
            return Optional.empty();
        }
    }

    private static String key(String deviceId) {
        return KEY_PREFIX + deviceId;
    }

    record StoredRoute(String nodeId, String protocol, long connectedAt) {
    }
}
