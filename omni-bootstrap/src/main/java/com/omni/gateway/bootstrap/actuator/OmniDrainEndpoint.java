package com.omni.gateway.bootstrap.actuator;

import com.omni.gateway.network.drain.NodeDrainService;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@Endpoint(id = "omnidrain")
public class OmniDrainEndpoint {

    private final NodeDrainService nodeDrainService;

    public OmniDrainEndpoint(NodeDrainService nodeDrainService) {
        this.nodeDrainService = nodeDrainService;
    }

    /**
     * POST /actuator/omnidrain?timeoutSec=120
     * 需编译保留参数名（pom 中 {@code parameters=true}，IDE 需开启 -parameters）。
     */
    @WriteOperation
    public Map<String, Object> drain(@Nullable Integer timeoutSec) throws Exception {
        int timeout = timeoutSec != null ? timeoutSec : 120;
        var result = nodeDrainService.drainNode(timeout).get(timeout + 5L, TimeUnit.SECONDS);
        return Map.of(
                "success", result.success(),
                "message", result.message(),
                "remainingSessions", result.remainingSessions());
    }
}
