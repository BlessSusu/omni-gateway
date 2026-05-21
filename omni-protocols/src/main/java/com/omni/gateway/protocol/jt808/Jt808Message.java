package com.omni.gateway.protocol.jt808;

public class Jt808Message {

    private int messageId;
    private int bodyLength;
    private String terminalPhone;
    private int serialNo;
    private byte[] body;
    /** 线上原始帧（含 0x7E 界符），供协议流量日志使用 */
    private byte[] rawFrame;

    public int getMessageId() {
        return messageId;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    public int getBodyLength() {
        return bodyLength;
    }

    public void setBodyLength(int bodyLength) {
        this.bodyLength = bodyLength;
    }

    public String getTerminalPhone() {
        return terminalPhone;
    }

    public void setTerminalPhone(String terminalPhone) {
        this.terminalPhone = terminalPhone;
    }

    public int getSerialNo() {
        return serialNo;
    }

    public void setSerialNo(int serialNo) {
        this.serialNo = serialNo;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public byte[] getRawFrame() {
        return rawFrame;
    }

    public void setRawFrame(byte[] rawFrame) {
        this.rawFrame = rawFrame;
    }
}
