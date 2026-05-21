package com.omni.gateway.protocol.gb28181;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parsed SIP message (GB28181 signaling over TCP).
 */
public class Gb28181Message {

    private boolean response;
    private String startLine;
    private String method;
    private String requestUri;
    private int statusCode;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private String body;
    private byte[] rawFrame;

    public boolean isResponse() {
        return response;
    }

    public void setResponse(boolean response) {
        this.response = response;
    }

    public String getStartLine() {
        return startLine;
    }

    public void setStartLine(String startLine) {
        this.startLine = startLine;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getRequestUri() {
        return requestUri;
    }

    public void setRequestUri(String requestUri) {
        this.requestUri = requestUri;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public Map<String, String> headers() {
        return Collections.unmodifiableMap(headers);
    }

    public String header(String name) {
        if (name == null) {
            return null;
        }
        for (Map.Entry<String, String> e : headers.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) {
                return e.getValue();
            }
        }
        return null;
    }

    public void addHeader(String name, String value) {
        headers.put(name, value);
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public byte[] getRawFrame() {
        return rawFrame;
    }

    public void setRawFrame(byte[] rawFrame) {
        this.rawFrame = rawFrame;
    }

    public boolean isRegister() {
        return "REGISTER".equalsIgnoreCase(method);
    }

    public boolean hasManscdpBody() {
        String ct = header("Content-Type");
        return ct != null && ct.toLowerCase().contains("manscdp");
    }
}
