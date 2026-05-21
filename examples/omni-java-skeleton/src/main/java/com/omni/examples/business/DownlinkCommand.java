package com.omni.examples.business;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 与网关 {@code CommandEnvelope} 对应的下行命令 JSON。
 */
public class DownlinkCommand {

    private String messageId;
    private String deviceId;
    private String protocol = "simple-frame";
    private String commandType;
    private JsonNode payload;
    private Long timeoutMs = 5000L;

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

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

    public String getCommandType() {
        return commandType;
    }

    public void setCommandType(String commandType) {
        this.commandType = commandType;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }

    public Long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(Long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
