package com.ai.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.HandshakeInfo;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebSocketHandshakeValidatorTest {

    private final WebSocketHandshakeValidator validator = new WebSocketHandshakeValidator(
            new AllowedOrigins(List.of("http://localhost:3000")), new JwtUtil(TestTokens.SECRET));

    @Test
    void acceptsAnAllowedOriginWithAValidToken() {
        JwtUtil.VerifiedToken token = validator.validate(handshake("http://localhost:3000", TestTokens.valid(42)));

        assertThat(token.userId()).isEqualTo(42L);
    }

    @Test
    void rejectsAForeignOrigin() {
        assertThatThrownBy(() -> validator.validate(handshake("https://evil.example", TestTokens.valid(42))))
                .isInstanceOf(TokenRejectedException.class);
    }

    @Test
    void rejectsAMissingOrigin() {
        assertThatThrownBy(() -> validator.validate(handshake(null, TestTokens.valid(42))))
                .isInstanceOf(TokenRejectedException.class);
    }

    @Test
    void rejectsAnExpiredToken() {
        assertThatThrownBy(() -> validator.validate(handshake("http://localhost:3000", TestTokens.expired(42))))
                .isInstanceOf(TokenRejectedException.class)
                .extracting(ex -> ((TokenRejectedException) ex).reason())
                .isEqualTo(TokenRejectedException.Reason.EXPIRED);
    }

    @Test
    void rejectsAMissingToken() {
        assertThatThrownBy(() -> validator.validate(handshake("http://localhost:3000", null)))
                .isInstanceOf(TokenRejectedException.class);
    }

    private HandshakeInfo handshake(String origin, String token) {
        HttpHeaders headers = new HttpHeaders();
        if (origin != null) {
            headers.setOrigin(origin);
        }
        URI uri = URI.create("ws://localhost:8089/ws" + (token == null ? "" : "?token=" + token));
        return new HandshakeInfo(uri, headers, Mono.empty(), null);
    }
}
