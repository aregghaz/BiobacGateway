package com.ai.gateway.filter;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DuplicateSlashNormalizingFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getURI().getRawPath();
        if (path == null || !path.contains("//")) {
            return chain.filter(exchange);
        }

        String normalizedPath = path.replaceAll("/{2,}", "/");
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate().path(normalizedPath).build();
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }
}
