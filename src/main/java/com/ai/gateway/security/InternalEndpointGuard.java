package com.ai.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Keeps the gateway's own {@code /api/internal/**} endpoints (notification publishing) to processes on this host.
 * A request proxied in from outside also arrives from loopback, so any proxy header means "not internal".
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class InternalEndpointGuard implements WebFilter {

    private static final List<String> PROXY_HEADERS = List.of("X-Forwarded-For", "Forwarded", "X-Real-IP");
    private static final Logger log = LoggerFactory.getLogger(InternalEndpointGuard.class);

    private final PublicPaths publicPaths;
    private final JsonErrorWriter errorWriter;

    public InternalEndpointGuard(PublicPaths publicPaths, JsonErrorWriter errorWriter) {
        this.publicPaths = publicPaths;
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().pathWithinApplication().value();
        if (!publicPaths.isGatewayInternal(path) || isLocalDirectCall(request)) {
            return chain.filter(exchange);
        }
        log.warn("Rejecting internal call to {} from {}", path, request.getRemoteAddress());
        return errorWriter.write(exchange, HttpStatus.NOT_FOUND, "Not found");
    }

    private boolean isLocalDirectCall(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        boolean loopback = remote != null && remote.getAddress() != null && remote.getAddress().isLoopbackAddress();
        boolean proxied = PROXY_HEADERS.stream().anyMatch(header -> request.getHeaders().containsKey(header));
        return loopback && !proxied;
    }
}
