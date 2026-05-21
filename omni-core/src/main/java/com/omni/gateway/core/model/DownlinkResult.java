package com.omni.gateway.core.model;

public class DownlinkResult {

    private String messageId;
    private String deviceId;
    private DownlinkStatus status;
    private String detail;
    private Long finishedAt;

    public static DownlinkResult of(String messageId, String deviceId, DownlinkStatus status, String detail) {
        DownlinkResult r = new DownlinkResult();
        r.messageId = messageId;
        r.deviceId = deviceId;
        r.status = status;
        r.detail = detail;
        r.finishedAt = System.currentTimeMillis();
        return r;
    }

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

    public DownlinkStatus getStatus() {
        return status;
    }

    public void setStatus(DownlinkStatus status) {
        this.status = status;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Long getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Long finishedAt) {
        this.finishedAt = finishedAt;
    }
}
