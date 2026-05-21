package com.omni.examples.simpleframe;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * simple-frame 正文 JSON（与网关 omni-protocols 字段一致）。
 */
public class SimpleFrameMessage {

    private String type;
    private String deviceId;
    private String messageId;
    private JsonNode payload;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }
}
