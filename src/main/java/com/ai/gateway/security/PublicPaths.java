package com.ai.gateway.security;

import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;
import java.util.stream.Stream;

/**
 * Routed paths that need no access token: login and token refresh, and API docs. Mirrors what the services
 * themselves leave open.
 */
@Component
public class PublicPaths {

    private static final List<String> ROUTED_SERVICES = List.of("users", "warehouse", "company", "attribute", "audit");

    private final List<PathPattern> publicPatterns;

    public PublicPaths() {
        PathPatternParser parser = PathPatternParser.defaultInstance;
        Stream<String> userAuth = Stream.of("/api/users/auth/**", "/api/users/login");
        Stream<String> apiDocs = ROUTED_SERVICES.stream().flatMap(service -> Stream.of(
                "/api/" + service + "/v3/api-docs/**",
                "/api/" + service + "/swagger-ui/**",
                "/api/" + service + "/swagger-ui.html"));
        this.publicPatterns = Stream.concat(userAuth, apiDocs).map(parser::parse).toList();
    }

    public boolean isPublic(HttpMethod method, String path) {
        if (HttpMethod.OPTIONS.equals(method)) {
            return true;
        }
        PathContainer container = PathContainer.parsePath(path);
        return publicPatterns.stream().anyMatch(pattern -> pattern.matches(container));
    }

    /**
     * Service-to-service APIs ({@code /api/<service>/internal/...}) are never exposed through the gateway.
     */
    public boolean isInternal(String path) {
        return PathContainer.parsePath(path).elements().stream()
                .anyMatch(element -> "internal".equalsIgnoreCase(element.value()));
    }
}
