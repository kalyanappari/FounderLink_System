package com.founderlink.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "gateway.security")
public class GatewaySecurityProperties {

    private List<String> publicPaths = new ArrayList<>();
    private List<String> publicGetPaths = new ArrayList<>();

    public List<String> getPublicPaths() {
        return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
        this.publicPaths = publicPaths;
    }

    public List<String> getPublicGetPaths() {
        return publicGetPaths;
    }

    public void setPublicGetPaths(List<String> publicGetPaths) {
        this.publicGetPaths = publicGetPaths;
    }


}
