package com.ai.gateway.security;

import java.util.regex.Pattern;
import java.util.Arrays;
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

    private static final String API_SEGMENT = "api";
    private static final String INTERNAL_SEGMENT = "internal";
    private static final Pattern SEGMENT_SEPARATORS = Pattern.compile("[/\\\\]");
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
     * Service-to-service APIs ({@code /api/<service>/internal/...}) are never exposed through the gateway, however the
     * path is spelled.
     */
    public boolean isInternal(String path) {
        return decodedSegments(path).stream().anyMatch(INTERNAL_SEGMENT::equalsIgnoreCase);
    }

    /** The gateway's own API for processes on this host ({@code /api/internal/...}), however the path is spelled. */
    public boolean isGatewayInternal(String path) {
        List<String> segments = decodedSegments(path);
        return segments.size() >= 2 && API_SEGMENT.equalsIgnoreCase(segments.get(0))
                && INTERNAL_SEGMENT.equalsIgnoreCase(segments.get(1));
    }

    /**
     * Path segments as WebFlux matches them to a handler: percent-decoded, without {@code ;matrix} parameters. A
     * decoded slash or backslash splits a segment too, so no encoding can hide an {@code internal} segment.
     */
    private List<String> decodedSegments(String path) {
        return PathContainer.parsePath(path).elements().stream()
                .filter(PathContainer.PathSegment.class::isInstance)
                .map(element -> ((PathContainer.PathSegment) element).valueToMatch())
                .flatMap(segment -> Arrays.stream(SEGMENT_SEPARATORS.split(segment)))
                .filter(segment -> !segment.isEmpty())
                .toList();
    }
}
