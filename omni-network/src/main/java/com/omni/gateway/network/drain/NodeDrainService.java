package com.omni.gateway.network.drain;

import com.omni.gateway.core.session.SessionRegistry;
import com.omni.gateway.network.server.PortListenerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class NodeDrainService {

    private static final Logger log = LoggerFactory.getLogger(NodeDrainService.class);

    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final PortListenerManager portListenerManager;
    private final SessionRegistry sessionRegistry;
    private volatile Runnable downlinkStopHook = () -> {};

    public NodeDrainService(PortListenerManager portListenerManager, SessionRegistry sessionRegistry) {
        this.portListenerManager = portListenerManager;
        this.sessionRegistry = sessionRegistry;
    }

    public void setDownlinkStopHook(Runnable downlinkStopHook) {
        this.downlinkStopHook = downlinkStopHook != null ? downlinkStopHook : () -> {};
    }

    public boolean isDraining() {
        return draining.get();
    }

    public CompletableFuture<DrainResult> drainNode(int timeoutSec) {
        if (!draining.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(new DrainResult(false, "already_draining", sessionRegistry.localSessionCount()));
        }
        log.info("Node drain started timeoutSec={}", timeoutSec);
        portListenerManager.setAcceptNewConnections(false);
        try {
            downlinkStopHook.run();
        } catch (Exception e) {
            log.warn("Downlink stop hook failed", e);
        }
        return CompletableFuture.supplyAsync(() -> {
            long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
            while (sessionRegistry.localSessionCount() > 0 && System.currentTimeMillis() < deadline) {
                try {
                    TimeUnit.MILLISECONDS.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            int remaining = sessionRegistry.localSessionCount();
            portListenerManager.stopAll();
            boolean ok = remaining == 0;
            String msg = ok ? "drained" : "timeout_sessions_remaining=" + remaining;
            log.info("Node drain finished ok={} remaining={}", ok, remaining);
            return new DrainResult(ok, msg, remaining);
        });
    }

    public record DrainResult(boolean success, String message, int remainingSessions) {
    }
}
