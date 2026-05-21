package com.omni.gateway.network.session;

import com.omni.gateway.core.session.DeviceSession;
import com.omni.gateway.core.session.SessionRegistry;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemorySessionRegistry implements SessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(InMemorySessionRegistry.class);

    private final ConcurrentHashMap<String, DeviceSession> sessions = new ConcurrentHashMap<>();
    private final boolean kickOldOnReauth;

    public InMemorySessionRegistry(boolean kickOldOnReauth) {
        this.kickOldOnReauth = kickOldOnReauth;
    }

    @Override
    public void bind(String deviceId, DeviceSession session) {
        sessions.compute(deviceId, (id, existing) -> {
            if (existing != null && existing != session && kickOldOnReauth) {
                Channel old = existing.getChannel();
                if (old.isActive()) {
                    log.info("Kicking old session for deviceId={} oldChannel={}", id, old.id().asShortText());
                    old.close();
                }
            }
            return session;
        });
    }

    @Override
    public void unbind(String deviceId) {
        if (deviceId != null) {
            sessions.remove(deviceId);
        }
    }

    @Override
    public void unbindIfSame(String deviceId, DeviceSession session) {
        if (deviceId != null && session != null) {
            sessions.remove(deviceId, session);
        }
    }

    @Override
    public Optional<DeviceSession> get(String deviceId) {
        return Optional.ofNullable(sessions.get(deviceId));
    }

    @Override
    public int localSessionCount() {
        return sessions.size();
    }
}
