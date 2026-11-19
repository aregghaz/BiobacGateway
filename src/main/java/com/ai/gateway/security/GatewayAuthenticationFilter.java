package com.ai.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Edge check for every routed request: internal service APIs are hidden, public paths pass, and everything else needs a
 * valid access token. The services verify the token again, because they can also be reached without the gateway.
 */
@Component
public class GatewayAuthenticationFilter implements GlobalFilter, Ordered {

    static final int ORDER = UntrustedHeaderStrippingFilter.ORDER + 1;
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Logger log = LoggerFactory.getLogger(GatewayAuthenticationFilter.class);

    private final PublicPaths publicPaths;
    private final JwtUtil jwtUtil;
    private final JsonErrorWriter errorWriter;

    public GatewayAuthenticationFilter(PublicPaths publicPaths, JwtUtil jwtUtil, JsonErrorWriter errorWriter) {
        this.publicPaths = publicPaths;
        this.jwtUtil = jwtUtil;
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().pathWithinApplication().value();

        if (publicPaths.isInternal(path)) {
            log.warn("Rejecting request to {}: internal API is not exposed", path);
            return errorWriter.write(exchange, HttpStatus.NOT_FOUND, "Not found");
        }
        if (publicPaths.isPublic(request.getMethod(), path)) {
            return chain.filter(exchange);
        }
        try {
            jwtUtil.verify(extractToken(request));
        } catch (TokenRejectedException ex) {
            log.warn("Rejecting request to {}: {}", path, ex.reason());
            return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED, ex.reason().message());
        }
        return chain.filter(exchange);
    }

    private String extractToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX) || header.length() == BEARER_PREFIX.length()) {
            throw new TokenRejectedException(TokenRejectedException.Reason.MISSING);
        }
        return header.substring(BEARER_PREFIX.length());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
