package com.omni.gateway.bootstrap.actuator;

import com.omni.gateway.bootstrap.config.GatewayConfigRefreshService;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Endpoint(id = "omniconfig")
public class OmniConfigEndpoint {

    private final GatewayConfigRefreshService refreshService;

    public OmniConfigEndpoint(GatewayConfigRefreshService refreshService) {
        this.refreshService = refreshService;
    }

    @ReadOperation
    public Map<String, Object> read() {
        var snap = refreshService.current();
        return Map.of(
                "configVersion", snap.getConfigVersion(),
                "listeners", snap.getListeners().size(),
                "readerIdleSeconds", snap.getReaderIdleSeconds(),
                "maxGlobalUplinkPending", snap.getMaxGlobalUplinkPending(),
                "connectionRatePerIp", snap.getSecurity().getConnectionRatePerIp()
        );
    }

    @WriteOperation
    public Map<String, Object> refresh() {
        var r = refreshService.refreshFromProperties();
        refreshService.refreshFromExternalFile();
        return Map.of("success", r.success(), "message", r.message(), "configVersion", r.configVersion());
    }
}
