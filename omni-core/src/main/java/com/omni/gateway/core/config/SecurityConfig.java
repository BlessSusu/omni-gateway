package com.omni.gateway.core.config;

import java.util.ArrayList;
import java.util.List;

public class SecurityConfig {

    private int connectionRatePerIp = 50;
    private List<String> ipDenyList = new ArrayList<>();
    private List<String> ipAllowList = new ArrayList<>();

    public int getConnectionRatePerIp() {
        return connectionRatePerIp;
    }

    public void setConnectionRatePerIp(int connectionRatePerIp) {
        this.connectionRatePerIp = connectionRatePerIp;
    }

    public List<String> getIpDenyList() {
        return ipDenyList;
    }

    public void setIpDenyList(List<String> ipDenyList) {
        this.ipDenyList = ipDenyList;
    }

    public List<String> getIpAllowList() {
        return ipAllowList;
    }

    public void setIpAllowList(List<String> ipAllowList) {
        this.ipAllowList = ipAllowList;
    }
}
