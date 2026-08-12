package com.ai.gateway;

import com.ai.gateway.security.JwtUtil;
import com.ai.gateway.service.WebSocketNotificationPublisher;
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
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.Map;
@Configuration
public class WebSocketConfig implements WebSocketHandler {

    private final ObjectMapper objectMapper;
    private final WebSocketNotificationPublisher publisher;
    private final JwtUtil jwtUtil;
    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    public WebSocketConfig(ObjectMapper objectMapper, WebSocketNotificationPublisher publisher, JwtUtil jwtUtil) {
        this.objectMapper = objectMapper;
        this.publisher = publisher;
        this.jwtUtil = jwtUtil;
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
        Long connectedUserId;
        try {
            connectedUserId = extractAuthenticatedUserId(session);
        } catch (Exception exception) {

            return session.close(CloseStatus.POLICY_VIOLATION);
        }

        Mono<Void> incoming = session.receive().then();

        Mono<Void> outgoing = session.send(publisher.messagesFor(connectedUserId).map(notification -> {
            try {
                return session.textMessage(objectMapper.writeValueAsString(notification));
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Could not serialize notification", exception);
            }
        }));

        return Mono.firstWithSignal(incoming, outgoing).doOnError(error -> log.warn("WebSocket session {} terminated with an error", session.getId(), error));

    }

    private Long extractAuthenticatedUserId(WebSocketSession session) {
        String token = UriComponentsBuilder.fromUri(session.getHandshakeInfo().getUri()).build().getQueryParams().getFirst("token");

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("WebSocket token is missing");
        }

        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        return jwtUtil.validateAndExtractUserId(token);
    }
}
