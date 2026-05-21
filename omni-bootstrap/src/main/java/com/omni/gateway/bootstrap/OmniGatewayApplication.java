package com.omni.gateway.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.omni.gateway")
@EnableConfigurationProperties(OmniGatewayProperties.class)
@EnableScheduling
public class OmniGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(OmniGatewayApplication.class, args);
    }
}
