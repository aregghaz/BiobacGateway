package com.ai.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class AllowedOrigins {

    static final String DEFAULT_ORIGINS =
            "http://localhost:3000,http://localhost:3001,https://kinomino.online,https://kinomino.ru";

    private final List<String> origins;

    public AllowedOrigins(@Value("${gateway.allowed-origins:" + DEFAULT_ORIGINS + "}") List<String> origins) {
        this.origins = origins.stream().map(String::trim).filter(origin -> !origin.isEmpty()).toList();
    }

    public List<String> asList() {
        return origins;
    }

    public boolean isAllowed(String origin) {
        return origin != null && origins.contains(origin);
    }
}
