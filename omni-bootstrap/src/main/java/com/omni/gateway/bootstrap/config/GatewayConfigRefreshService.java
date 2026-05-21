package com.omni.gateway.bootstrap.config;

import com.omni.gateway.bootstrap.OmniGatewayProperties;
import com.omni.gateway.core.config.GatewayConfigSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * L0/L1 配置热更：刷新内存快照（嗅探阈值、安全策略、插件优先级等对新连接生效）。
 */
@Service
public class GatewayConfigRefreshService {

    private static final Logger log = LoggerFactory.getLogger(GatewayConfigRefreshService.class);

    private final AtomicReference<GatewayConfigSnapshot> configRef;
    private final OmniGatewayProperties properties;
    private final AtomicLong lastModified = new AtomicLong(0);

    public GatewayConfigRefreshService(AtomicReference<GatewayConfigSnapshot> configRef,
                                       OmniGatewayProperties properties) {
        this.configRef = configRef;
        this.properties = properties;
    }

    public GatewayConfigSnapshot current() {
        return configRef.get();
    }

    public synchronized RefreshResult refreshFromProperties() {
        GatewayConfigSnapshot snap = properties.toSnapshot();
        configRef.set(snap);
        log.info("Config refreshed from properties version={}", snap.getConfigVersion());
        return RefreshResult.ok(snap.getConfigVersion());
    }

    @SuppressWarnings("unchecked")
    public synchronized RefreshResult refreshFromExternalFile() {
        String path = properties.getConfig().getExternalFile();
        if (path == null || path.isBlank()) {
            return RefreshResult.skipped("no external file");
        }
        try {
            Path file = Path.of(path);
            if (!Files.exists(file)) {
                return RefreshResult.failed("file not found: " + path);
            }
            long mod = Files.getLastModifiedTime(file).toMillis();
            if (mod == lastModified.get() && configRef.get().getConfigVersion() == properties.getGateway().getConfigVersion()) {
                return RefreshResult.skipped("unchanged");
            }
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(Files.readString(file));
            Object omni = root != null ? root.get("omni") : null;
            if (omni instanceof Map<?, ?> omniMap) {
                applyGatewayMap((Map<String, Object>) omniMap.get("gateway"));
                applySecurityMap((Map<String, Object>) omniMap.get("security"));
                applyLoggingMap((Map<String, Object>) omniMap.get("logging"));
            }
            lastModified.set(mod);
            return refreshFromProperties();
        } catch (Exception e) {
            log.error("External config reload failed", e);
            return RefreshResult.failed(e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${omni.config.poll-interval-ms:30000}")
    public void pollExternalFile() {
        if (properties.getConfig().getExternalFile() != null && !properties.getConfig().getExternalFile().isBlank()) {
            refreshFromExternalFile();
        }
    }

    private void applyGatewayMap(Map<String, Object> gateway) {
        if (gateway == null) {
            return;
        }
        if (gateway.get("config-version") instanceof Number n) {
            properties.getGateway().setConfigVersion(n.longValue());
        }
        if (gateway.get("reader-idle-seconds") instanceof Number n) {
            properties.getGateway().setReaderIdleSeconds(n.intValue());
        }
    }

    private void applySecurityMap(Map<String, Object> security) {
        if (security == null) {
            return;
        }
        if (security.get("connection-rate-per-ip") instanceof Number n) {
            properties.getSecurity().setConnectionRatePerIp(n.intValue());
        }
    }

    private void applyLoggingMap(Map<String, Object> logging) {
        if (logging == null) {
            return;
        }
        if (logging.get("protocol-hex-enabled") instanceof Boolean b) {
            properties.getLogging().setProtocolHexEnabled(b);
        }
        if (logging.get("json-enabled") instanceof Boolean b) {
            properties.getLogging().setJsonEnabled(b);
        }
    }

    public record RefreshResult(boolean success, String message, long configVersion) {
        static RefreshResult ok(long v) {
            return new RefreshResult(true, "ok", v);
        }

        static RefreshResult skipped(String msg) {
            return new RefreshResult(true, msg, 0);
        }

        static RefreshResult failed(String msg) {
            return new RefreshResult(false, msg, 0);
        }
    }
}
