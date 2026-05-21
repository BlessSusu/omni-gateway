package com.omni.gateway.core.config;

public class TlsConfig {

    private boolean enabled;
    private boolean mtlsEnabled;
    private String certPath = "classpath:certs/server.pem";
    private String keyPath = "classpath:certs/server-key.pem";
    private String trustCertPath = "classpath:certs/ca.pem";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isMtlsEnabled() {
        return mtlsEnabled;
    }

    public void setMtlsEnabled(boolean mtlsEnabled) {
        this.mtlsEnabled = mtlsEnabled;
    }

    public String getCertPath() {
        return certPath;
    }

    public void setCertPath(String certPath) {
        this.certPath = certPath;
    }

    public String getKeyPath() {
        return keyPath;
    }

    public void setKeyPath(String keyPath) {
        this.keyPath = keyPath;
    }

    public String getTrustCertPath() {
        return trustCertPath;
    }

    public void setTrustCertPath(String trustCertPath) {
        this.trustCertPath = trustCertPath;
    }
}
