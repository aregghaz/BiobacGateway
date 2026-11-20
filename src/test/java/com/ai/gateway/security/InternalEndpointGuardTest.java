package com.ai.gateway.security;

import java.net.URI;
import org.springframework.http.HttpMethod;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.ParameterizedTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class InternalEndpointGuardTest {

    private static final String PUBLISH = "/api/internal/ws/publish";

    private final InternalEndpointGuard guard = new InternalEndpointGuard(new PublicPaths(),
            new JsonErrorWriter(new ObjectMapper()));

    @Test
    void allowsADirectCallFromThisHost() {
        assertThat(passes(MockServerHttpRequest.post(PUBLISH).remoteAddress(address("127.0.0.1")))).isTrue();
    }

    @Test
    void rejectsACallFromAnotherHost() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post(PUBLISH).remoteAddress(address("203.0.113.9")));

        assertThat(run(exchange)).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsACallProxiedInFromOutside() {
        assertThat(passes(MockServerHttpRequest.post(PUBLISH).remoteAddress(address("127.0.0.1"))
                .header("X-Forwarded-For", "203.0.113.9"))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/%69nternal/ws/publish",
            "/api/internal;x=1/ws/publish",
            "/api/%49NTERNAL/ws/publish",
            "/api//internal/ws/publish"})
    void rejectsEncodedSpellingsFromAnotherHost(String path) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.POST, URI.create(path)).remoteAddress(address("203.0.113.9")));

        assertThat(run(exchange)).as(path).isFalse();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void ignoresOtherPaths() {
        assertThat(passes(MockServerHttpRequest.get("/api/warehouse/unit").remoteAddress(address("203.0.113.9"))))
                .isTrue();
    }

    private InetSocketAddress address(String ip) {
        return new InetSocketAddress(ip, 50000);
    }

    private boolean passes(MockServerHttpRequest.BaseBuilder<?> request) {
        return run(MockServerWebExchange.from(request));
    }

    private boolean run(MockServerWebExchange exchange) {
        AtomicBoolean passed = new AtomicBoolean(false);
        guard.filter(exchange, next -> {
            passed.set(true);
            return Mono.empty();
        }).block();
        return passed.get();
    }
}
