package com.omni.gateway.bootstrap;

import com.omni.gateway.core.config.GatewayConfigSnapshot;
import com.omni.gateway.core.config.PortListenerConfig;
import com.omni.gateway.core.config.SecurityConfig;
import com.omni.gateway.core.config.SniffConfig;
import com.omni.gateway.core.config.TlsConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "omni")
public class OmniGatewayProperties {

    private String nodeId = "omni-gateway-local";
    private Gateway gateway = new Gateway();
    private Kafka kafka = new Kafka();
    private Downlink downlink = new Downlink();
    private Security security = new Security();
    private Config config = new Config();
    private Backpressure backpressure = new Backpressure();
    private Logging logging = new Logging();
    private Session session = new Session();
    private Tls tls = new Tls();
    private Observability observability = new Observability();

    public long resolveSessionRedisTtlSeconds() {
        if (session.getRedisTtlSeconds() > 0) {
            return session.getRedisTtlSeconds();
        }
        return Math.max(60L, (long) gateway.getReaderIdleSeconds() * 3);
    }

    public GatewayConfigSnapshot toSnapshot() {
        GatewayConfigSnapshot snap = new GatewayConfigSnapshot();
        snap.setConfigVersion(gateway.getConfigVersion());
        snap.setReaderIdleSeconds(gateway.getReaderIdleSeconds());
        snap.setKickOldOnReauth(gateway.isKickOldOnReauth());
        snap.setMaxGlobalUplinkPending(backpressure.getMaxGlobalPending());
        snap.setMaxPerChannelUplinkPending(backpressure.getMaxPerChannelPending());
        SecurityConfig sec = new SecurityConfig();
        sec.setConnectionRatePerIp(security.getConnectionRatePerIp());
        sec.setIpDenyList(security.getIpDenyList());
        sec.setIpAllowList(security.getIpAllowList());
        snap.setSecurity(sec);
        List<PortListenerConfig> listeners = new ArrayList<>();
        for (Listener l : gateway.getListeners()) {
            PortListenerConfig plc = new PortListenerConfig();
            plc.setPort(l.getPort());
            plc.setPlugins(l.getPlugins());
            plc.setPluginPriority(l.getPluginPriority());
            SniffConfig sniff = new SniffConfig();
            sniff.setMaxBytes(l.getSniff().getMaxBytes());
            sniff.setTimeoutMs(l.getSniff().getTimeoutMs());
            sniff.setMinProbeLength(l.getSniff().getMinProbeLength());
            plc.setSniff(sniff);
            plc.setTls(l.isTls());
            listeners.add(plc);
        }
        snap.setListeners(listeners);
        TlsConfig tlsConfig = new TlsConfig();
        tlsConfig.setEnabled(tls.isEnabled());
        tlsConfig.setMtlsEnabled(tls.isMtlsEnabled());
        tlsConfig.setCertPath(tls.getCertPath());
        tlsConfig.setKeyPath(tls.getKeyPath());
        tlsConfig.setTrustCertPath(tls.getTrustCertPath());
        snap.setTls(tlsConfig);
        return snap;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public Gateway getGateway() {
        return gateway;
    }

    public void setGateway(Gateway gateway) {
        this.gateway = gateway;
    }

    public Kafka getKafka() {
        return kafka;
    }

    public void setKafka(Kafka kafka) {
        this.kafka = kafka;
    }

    public Downlink getDownlink() {
        return downlink;
    }

    public void setDownlink(Downlink downlink) {
        this.downlink = downlink;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Config getConfig() {
        return config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public Backpressure getBackpressure() {
        return backpressure;
    }

    public void setBackpressure(Backpressure backpressure) {
        this.backpressure = backpressure;
    }

    public Logging getLogging() {
        return logging;
    }

    public void setLogging(Logging logging) {
        this.logging = logging;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public Tls getTls() {
        return tls;
    }

    public void setTls(Tls tls) {
        this.tls = tls;
    }

    public Observability getObservability() {
        return observability;
    }

    public void setObservability(Observability observability) {
        this.observability = observability;
    }

    public static class Session {
        private boolean redisEnabled;
        private long redisTtlSeconds;

        public boolean isRedisEnabled() {
            return redisEnabled;
        }

        public void setRedisEnabled(boolean redisEnabled) {
            this.redisEnabled = redisEnabled;
        }

        public long getRedisTtlSeconds() {
            return redisTtlSeconds;
        }

        public void setRedisTtlSeconds(long redisTtlSeconds) {
            this.redisTtlSeconds = redisTtlSeconds;
        }
    }

    public static class Tls {
        private boolean enabled;
        private boolean mtlsEnabled;
        private String certPath = "classpath:certs/server.pem";
        private String keyPath = "classpath:certs/server-key.pem";
        private String trustCertPath = "classpath:certs/ca.pem";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isMtlsEnabled() {
            return mtlsEnabled;
        }

        public void setMtlsEnabled(boolean mtlsEnabled) {
            this.mtlsEnabled = mtlsEnabled;
        }

        public String getCertPath() {
            return certPath;
        }

        public void setCertPath(String certPath) {
            this.certPath = certPath;
        }

        public String getKeyPath() {
            return keyPath;
        }

        public void setKeyPath(String keyPath) {
            this.keyPath = keyPath;
        }

        public String getTrustCertPath() {
            return trustCertPath;
        }

        public void setTrustCertPath(String trustCertPath) {
            this.trustCertPath = trustCertPath;
        }
    }

    public static class Observability {
        private boolean traceEnabled = true;

        public boolean isTraceEnabled() {
            return traceEnabled;
        }

        public void setTraceEnabled(boolean traceEnabled) {
            this.traceEnabled = traceEnabled;
        }
    }

    public static class Logging {
        /** 是否打印完整协议帧十六进制（recv/send 流量日志） */
        private boolean protocolHexEnabled = false;
        private boolean jsonEnabled = true;

        public boolean isProtocolHexEnabled() {
            return protocolHexEnabled;
        }

        public void setProtocolHexEnabled(boolean protocolHexEnabled) {
            this.protocolHexEnabled = protocolHexEnabled;
        }

        public boolean isJsonEnabled() {
            return jsonEnabled;
        }

        public void setJsonEnabled(boolean jsonEnabled) {
            this.jsonEnabled = jsonEnabled;
        }
    }

    public static class Gateway {
        private long configVersion = 1;
        private int readerIdleSeconds = 120;
        private boolean kickOldOnReauth = true;
        private List<Listener> listeners = new ArrayList<>();

        public long getConfigVersion() {
            return configVersion;
        }

        public void setConfigVersion(long configVersion) {
            this.configVersion = configVersion;
        }

        public int getReaderIdleSeconds() {
            return readerIdleSeconds;
        }

        public void setReaderIdleSeconds(int readerIdleSeconds) {
            this.readerIdleSeconds = readerIdleSeconds;
        }

        public boolean isKickOldOnReauth() {
            return kickOldOnReauth;
        }

        public void setKickOldOnReauth(boolean kickOldOnReauth) {
            this.kickOldOnReauth = kickOldOnReauth;
        }

        public List<Listener> getListeners() {
            return listeners;
        }

        public void setListeners(List<Listener> listeners) {
            this.listeners = listeners;
        }
    }

    public static class Listener {
        private int port;
        private boolean tls;
        private List<String> plugins = new ArrayList<>();
        private List<String> pluginPriority = new ArrayList<>();
        private SniffProps sniff = new SniffProps();

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public boolean isTls() {
            return tls;
        }

        public void setTls(boolean tls) {
            this.tls = tls;
        }

        public List<String> getPlugins() {
            return plugins;
        }

        public void setPlugins(List<String> plugins) {
            this.plugins = plugins;
        }

        public List<String> getPluginPriority() {
            return pluginPriority;
        }

        public void setPluginPriority(List<String> pluginPriority) {
            this.pluginPriority = pluginPriority;
        }

        public SniffProps getSniff() {
            return sniff;
        }

        public void setSniff(SniffProps sniff) {
            this.sniff = sniff;
        }
    }

    public static class SniffProps {
        private int maxBytes = 256;
        private int timeoutMs = 5000;
        private int minProbeLength = 2;

        public int getMaxBytes() {
            return maxBytes;
        }

        public void setMaxBytes(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public int getMinProbeLength() {
            return minProbeLength;
        }

        public void setMinProbeLength(int minProbeLength) {
            this.minProbeLength = minProbeLength;
        }
    }

    public static class Security {
        private int connectionRatePerIp = 50;
        private List<String> ipDenyList = new ArrayList<>();
        private List<String> ipAllowList = new ArrayList<>();

        public int getConnectionRatePerIp() {
            return connectionRatePerIp;
        }

        public void setConnectionRatePerIp(int connectionRatePerIp) {
            this.connectionRatePerIp = connectionRatePerIp;
        }

        public List<String> getIpDenyList() {
            return ipDenyList;
        }

        public void setIpDenyList(List<String> ipDenyList) {
            this.ipDenyList = ipDenyList;
        }

        public List<String> getIpAllowList() {
            return ipAllowList;
        }

        public void setIpAllowList(List<String> ipAllowList) {
            this.ipAllowList = ipAllowList;
        }
    }

    public static class Config {
        private String externalFile = "";
        private long pollIntervalMs = 30000;

        public String getExternalFile() {
            return externalFile;
        }

        public void setExternalFile(String externalFile) {
            this.externalFile = externalFile;
        }

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }
    }

    public static class Backpressure {
        private int maxGlobalPending = 5000;
        private int maxPerChannelPending = 32;

        public int getMaxGlobalPending() {
            return maxGlobalPending;
        }

        public void setMaxGlobalPending(int maxGlobalPending) {
            this.maxGlobalPending = maxGlobalPending;
        }

        public int getMaxPerChannelPending() {
            return maxPerChannelPending;
        }

        public void setMaxPerChannelPending(int maxPerChannelPending) {
            this.maxPerChannelPending = maxPerChannelPending;
        }
    }

    public static class Kafka {
        private String bootstrapServers = "localhost:9092";
        private String uplinkTopic = "omni.device.uplink";
        private String lifecycleTopic = "omni.device.lifecycle";
        private boolean enabled = true;
        private boolean lifecycleEnabled = true;
        private int failureThreshold = 3;

        public String getBootstrapServers() {
            return bootstrapServers;
        }

        public void setBootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }

        public String getUplinkTopic() {
            return uplinkTopic;
        }

        public void setUplinkTopic(String uplinkTopic) {
            this.uplinkTopic = uplinkTopic;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getLifecycleTopic() {
            return lifecycleTopic;
        }

        public void setLifecycleTopic(String lifecycleTopic) {
            this.lifecycleTopic = lifecycleTopic;
        }

        public boolean isLifecycleEnabled() {
            return lifecycleEnabled;
        }

        public void setLifecycleEnabled(boolean lifecycleEnabled) {
            this.lifecycleEnabled = lifecycleEnabled;
        }

        public int getFailureThreshold() {
            return failureThreshold;
        }

        public void setFailureThreshold(int failureThreshold) {
            this.failureThreshold = failureThreshold;
        }
    }

    public static class Downlink {
        private boolean enabled = true;
        private String topic = "omni.command.downlink";
        private String nodeTopicPattern = "omni.command.downlink.{nodeId}";
        private boolean routerEnabled;
        private String routerConsumerGroup = "omni-gateway-downlink-router";
        private String resultTopic = "omni.command.downlink.result";
        private boolean resultEnabled = true;
        private String consumerGroupSuffix = "local";

        public String resolveNodeTopic(String nodeId) {
            return nodeTopicPattern.replace("{nodeId}", nodeId);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public String getNodeTopicPattern() {
            return nodeTopicPattern;
        }

        public void setNodeTopicPattern(String nodeTopicPattern) {
            this.nodeTopicPattern = nodeTopicPattern;
        }

        public boolean isRouterEnabled() {
            return routerEnabled;
        }

        public void setRouterEnabled(boolean routerEnabled) {
            this.routerEnabled = routerEnabled;
        }

        public String getRouterConsumerGroup() {
            return routerConsumerGroup;
        }

        public void setRouterConsumerGroup(String routerConsumerGroup) {
            this.routerConsumerGroup = routerConsumerGroup;
        }

        public String getResultTopic() {
            return resultTopic;
        }

        public void setResultTopic(String resultTopic) {
            this.resultTopic = resultTopic;
        }

        public boolean isResultEnabled() {
            return resultEnabled;
        }

        public void setResultEnabled(boolean resultEnabled) {
            this.resultEnabled = resultEnabled;
        }

        public String getConsumerGroupSuffix() {
            return consumerGroupSuffix;
        }

        public void setConsumerGroupSuffix(String consumerGroupSuffix) {
            this.consumerGroupSuffix = consumerGroupSuffix;
        }
    }
}
