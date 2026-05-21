package com.omni.gateway.core.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public class ThingModel {

    private String deviceId;
    private String protocol;
    private String messageType;
    private Instant timestamp;
    private JsonNode payload;
    private String gatewayNodeId;
    private String traceId;

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }

    public String getGatewayNodeId() {
        return gatewayNodeId;
    }

    public void setGatewayNodeId(String gatewayNodeId) {
        this.gatewayNodeId = gatewayNodeId;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
