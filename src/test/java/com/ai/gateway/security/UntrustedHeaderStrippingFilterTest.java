package com.ai.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class UntrustedHeaderStrippingFilterTest {

    @Test
    void dropsServiceOnlyHeadersAndKeepsTheRest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/warehouse/unit")
                .header("X-Request-Id", "chosen-by-client")
                .header("X-Request-Forwarded", "true")
                .header("X-Service-Key", "guess")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token"));
        AtomicReference<HttpHeaders> forwarded = new AtomicReference<>();

        new UntrustedHeaderStrippingFilter().filter(exchange, next -> {
            forwarded.set(next.getRequest().getHeaders());
            return Mono.empty();
        }).block();

        assertThat(forwarded.get()).doesNotContainKeys("X-Request-Id", "X-Request-Forwarded", "X-Service-Key");
        assertThat(forwarded.get().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer token");
    }
}
