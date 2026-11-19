package com.ai.gateway.security;

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

    private final InternalEndpointGuard guard = new InternalEndpointGuard(new JsonErrorWriter(new ObjectMapper()));

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
