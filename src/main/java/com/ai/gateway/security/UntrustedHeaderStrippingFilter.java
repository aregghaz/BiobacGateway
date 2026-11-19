package com.ai.gateway.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Drops headers only BioBac services may set. Browsers could otherwise choose the audit request id, mark a call as
 * service-forwarded, or present a service key.
 */
@Component
public class UntrustedHeaderStrippingFilter implements GlobalFilter, Ordered {

    static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 100;
    static final List<String> SERVICE_ONLY_HEADERS = List.of("X-Request-Id", "X-Request-Forwarded", "X-Service-Key");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest stripped = exchange.getRequest().mutate()
                .headers(headers -> SERVICE_ONLY_HEADERS.forEach(headers::remove))
                .build();
        return chain.filter(exchange.mutate().request(stripped).build());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
