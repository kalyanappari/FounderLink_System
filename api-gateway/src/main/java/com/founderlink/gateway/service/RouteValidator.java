package com.founderlink.gateway.service;

import com.founderlink.gateway.config.GatewaySecurityProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;

@Service
public class RouteValidator {

    private final GatewaySecurityProperties gatewaySecurityProperties;
    private final AntPathMatcher pathMatcher;

    public RouteValidator(GatewaySecurityProperties gatewaySecurityProperties) {
        this.gatewaySecurityProperties = gatewaySecurityProperties;
        this.pathMatcher = new AntPathMatcher();
        this.pathMatcher.setCaseSensitive(true);
    }

    public boolean isSecured(ServerHttpRequest request) {
        String requestPath = normalizePath(request.getPath().value());
        String method = request.getMethod() != null ? request.getMethod().name() : "";

        // 1. Check universally public paths
        boolean isPublicPath = gatewaySecurityProperties.getPublicPaths().stream()
                .filter(StringUtils::hasText)
                .map(this::normalizePath)
                .anyMatch(publicPath -> pathMatcher.match(publicPath, requestPath));

        if (isPublicPath) {
            return false;
        }

        // 2. Check public GET paths
        if ("GET".equalsIgnoreCase(method)) {
            boolean isPublicGetPath = gatewaySecurityProperties.getPublicGetPaths().stream()
                    .filter(StringUtils::hasText)
                    .map(this::normalizePath)
                    .anyMatch(publicGetPath -> pathMatcher.match(publicGetPath, requestPath));
            
            if (isPublicGetPath) {
                return false;
            }
        }

        return true;
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        if (path.length() > 1 && path.endsWith("/")) {
            return path.substring(0, path.length() - 1);
        }
        return path;
    }
}
