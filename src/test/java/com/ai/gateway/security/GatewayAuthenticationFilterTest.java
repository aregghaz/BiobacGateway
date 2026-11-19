package com.ai.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayAuthenticationFilterTest {

    private final GatewayAuthenticationFilter filter = new GatewayAuthenticationFilter(
            new PublicPaths(), new JwtUtil(TestTokens.SECRET), new JsonErrorWriter(new ObjectMapper()));

    @Test
    void routesARequestWithAValidToken() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/warehouse/unit")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid(42)));

        assertThat(run(exchange)).isTrue();
    }

    @Test
    void rejectsARequestWithoutAToken() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/company/account"));

        assertThat(run(exchange)).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(body(exchange)).contains("\"message\":\"Authentication required\"");
    }

    @Test
    void rejectsAnExpiredTokenWithTheMessageTheUiRefreshesOn() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/company/account")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.expired(42)));

        assertThat(run(exchange)).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(body(exchange)).contains("\"message\":\"Access token expired\"");
    }

    @Test
    void rejectsATokenSignedWithAnotherKey() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/company/balance/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.signedWithAnotherKey(1)));

        assertThat(run(exchange)).isFalse();
        assertThat(body(exchange)).contains("\"message\":\"Invalid access token.\"");
    }

    @Test
    void letsLoginAndRefreshThroughWithoutAToken() {
        assertThat(run(exchange(MockServerHttpRequest.post("/api/users/auth/login")))).isTrue();
        assertThat(run(exchange(MockServerHttpRequest.post("/api/users/auth/refresh-token")))).isTrue();
    }

    @Test
    void requiresLoginForThePermissionCatalogue() {
        assertThat(run(exchange(MockServerHttpRequest.get("/api/users/roles/permissions")))).isFalse();
    }

    @Test
    void hidesInternalServiceApisEvenWithAValidToken() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/users/internal/users/1/permissions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid(42)));

        assertThat(run(exchange)).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> request) {
        return MockServerWebExchange.from(request);
    }

    private boolean run(MockServerWebExchange exchange) {
        AtomicBoolean routed = new AtomicBoolean(false);
        GatewayFilterChain chain = routedExchange -> {
            routed.set(true);
            return reactor.core.publisher.Mono.empty();
        };
        filter.filter(exchange, chain).block();
        return routed.get();
    }

    private String body(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString().block();
    }
}
