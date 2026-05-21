package com.omni.gateway.bootstrap.config;

import com.omni.gateway.bootstrap.OmniGatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.yaml.snakeyaml.Yaml;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Pulls omni-gateway.yaml from Nacos Open API and applies via {@link GatewayConfigRefreshService}.
 */
@Service
@ConditionalOnProperty(name = "omni.nacos.enabled", havingValue = "true")
public class NacosConfigPullService {

    private static final Logger log = LoggerFactory.getLogger(NacosConfigPullService.class);

    private final OmniGatewayProperties properties;
    private final GatewayConfigRefreshService refreshService;
    private final RestTemplate restTemplate = new RestTemplate();
    private volatile String lastContent = "";

    public NacosConfigPullService(OmniGatewayProperties properties,
                                  GatewayConfigRefreshService refreshService) {
        this.properties = properties;
        this.refreshService = refreshService;
    }

    @Scheduled(fixedDelayString = "${omni.nacos.poll-interval-ms:30000}")
    public void poll() {
        try {
            var nacos = properties.getNacos();
            String url = "http://" + nacos.getServerAddr() + "/nacos/v1/cs/configs"
                    + "?dataId=" + enc(nacos.getDataId())
                    + "&group=" + enc(nacos.getGroup());
            String content = restTemplate.getForObject(url, String.class);
            if (content == null || content.isBlank() || content.equals(lastContent)) {
                return;
            }
            lastContent = content;
            applyYaml(content);
            log.info("Nacos config applied dataId={}", nacos.getDataId());
        } catch (Exception e) {
            log.warn("Nacos config pull failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyYaml(String yamlContent) {
        Yaml yaml = new Yaml();
        Map<String, Object> root = yaml.load(yamlContent);
        if (root == null) {
            return;
        }
        Object omni = root.get("omni");
        if (omni instanceof Map<?, ?> omniMap) {
            Object gateway = omniMap.get("gateway");
            if (gateway instanceof Map<?, ?> gw) {
                if (gw.get("config-version") instanceof Number n) {
                    properties.getGateway().setConfigVersion(n.longValue());
                }
                if (gw.get("reader-idle-seconds") instanceof Number n) {
                    properties.getGateway().setReaderIdleSeconds(n.intValue());
                }
            }
            Object security = omniMap.get("security");
            if (security instanceof Map<?, ?> sec) {
                if (sec.get("connection-rate-per-ip") instanceof Number n) {
                    properties.getSecurity().setConnectionRatePerIp(n.intValue());
                }
            }
        }
        refreshService.refreshFromProperties();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
