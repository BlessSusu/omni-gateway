package com.omni.gateway.network.session;

import com.omni.gateway.core.session.DeviceSession;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemorySessionRegistryTest {

    @Test
    void bindKickOldSession() {
        InMemorySessionRegistry registry = new InMemorySessionRegistry(true);
        EmbeddedChannel oldCh = new EmbeddedChannel();
        EmbeddedChannel newCh = new EmbeddedChannel();
        DeviceSession oldSession = new DeviceSession(oldCh, 9000);
        DeviceSession newSession = new DeviceSession(newCh, 9000);
        oldSession.setDeviceId("d1");
        newSession.setDeviceId("d1");

        registry.bind("d1", oldSession);
        registry.bind("d1", newSession);

        assertTrue(!oldCh.isActive() || registry.get("d1").orElseThrow() == newSession);
        assertEquals(1, registry.localSessionCount());
        oldCh.finish();
        newCh.finish();
    }

    @Test
    void unbindIfSame() {
        InMemorySessionRegistry registry = new InMemorySessionRegistry(false);
        EmbeddedChannel ch = new EmbeddedChannel();
        DeviceSession session = new DeviceSession(ch, 9000);
        session.setDeviceId("d2");
        registry.bind("d2", session);
        registry.unbindIfSame("d2", session);
        assertTrue(registry.get("d2").isEmpty());
        ch.finish();
    }
}
