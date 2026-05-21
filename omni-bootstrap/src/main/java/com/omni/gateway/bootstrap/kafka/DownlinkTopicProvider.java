package com.omni.gateway.bootstrap.kafka;

import com.omni.gateway.bootstrap.OmniGatewayProperties;
import org.springframework.stereotype.Component;

@Component
public class DownlinkTopicProvider {

    private final String nodeTopic;

    public DownlinkTopicProvider(OmniGatewayProperties properties) {
        this.nodeTopic = properties.getDownlink().resolveNodeTopic(properties.getNodeId());
    }

    public String nodeTopic() {
        return nodeTopic;
    }
}
