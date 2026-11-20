package com.ai.gateway;

import com.ai.gateway.security.WebSocketHandshakeValidator;
import com.ai.gateway.security.JwtUtil.VerifiedToken;
import com.ai.gateway.security.TokenRejectedException;
import com.ai.gateway.service.WebSocketNotificationPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

@Configuration
public class WebSocketConfig implements WebSocketHandler {

    /** Sent when the access token runs out; the client reconnects with its refreshed token. */
    static final CloseStatus TOKEN_EXPIRED = new CloseStatus(4001, "Token expired");

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private final ObjectMapper objectMapper;
    private final WebSocketNotificationPublisher publisher;
    private final WebSocketHandshakeValidator handshakeValidator;
    private final Clock clock;

    public WebSocketConfig(ObjectMapper objectMapper, WebSocketNotificationPublisher publisher,
                           WebSocketHandshakeValidator handshakeValidator) {
        this.objectMapper = objectMapper;
        this.publisher = publisher;
        this.handshakeValidator = handshakeValidator;
        this.clock = Clock.systemUTC();
    }

    @Bean
    public HandlerMapping webSocketHandlerMapping() {
        SimpleUrlHandlerMapping mapping = new SimpleUrlHandlerMapping();
        mapping.setUrlMap(Map.of("/ws", this));
        mapping.setOrder(-1);
        return mapping;
    }

    @Bean
    public WebSocketHandlerAdapter webSocketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        VerifiedToken token;
        try {
            token = handshakeValidator.validate(session.getHandshakeInfo());
        } catch (TokenRejectedException ex) {
            log.warn("Rejecting WebSocket handshake {}: {}", session.getId(), ex.reason());
            return session.close(CloseStatus.POLICY_VIOLATION);
        }

        Duration remaining = Duration.between(clock.instant(), token.expiresAt());
        if (remaining.isNegative() || remaining.isZero()) {
            return session.close(TOKEN_EXPIRED);
        }

        Mono<Void> incoming = session.receive().then();
        Mono<Void> outgoing = session.send(publisher.messagesFor(token.userId()).map(notification -> {
            try {
                return session.textMessage(objectMapper.writeValueAsString(notification));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Could not serialize notification", exception);
            }
        }));
        // a session must not outlive the token that opened it, or a logged-out or stolen token keeps receiving events
        Mono<Void> expiry = Mono.delay(remaining).then(Mono.defer(() -> {
            log.info("Closing WebSocket session {} for user {}: token expired", session.getId(), token.userId());
            return session.close(TOKEN_EXPIRED);
        }));

        return Mono.firstWithSignal(incoming, outgoing, expiry)
                .doOnError(error -> log.warn("WebSocket session {} terminated with an error", session.getId(), error));
    }
}
