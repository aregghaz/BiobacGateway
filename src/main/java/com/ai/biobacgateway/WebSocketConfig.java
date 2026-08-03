package com.ai.biobacgateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class WebSocketConfig implements WebSocketHandler, GlobalFilter {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);
    private static final Map<String, String> NOTIFICATION_EVENTS = Map.of(
            "/api/company/sale/pre-mobile", "pre-mobile-sale-created",
            "/api/company/reminder", "reminder-created",
            "/api/warehouse/transfer/product", "product-transfer-created");
    private final Set<WebSocketSession> clients = ConcurrentHashMap.newKeySet();
    private final Sinks.Many<String> messageChannel = Sinks.many().multicast().directBestEffort();
    private final ObjectMapper objectMapper;

    public WebSocketConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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


    private synchronized void broadcastNotification(String event, String source) {
        try {
            String json = objectMapper.writeValueAsString(Map.of("event", event, "source", source, "timestamp", Instant.now().toString()));

            Sinks.EmitResult result = messageChannel.tryEmitNext(json);

            if (result != Sinks.EmitResult.OK && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
                log.warn("Could not send WebSocket notification: {}", result);
            }
        } catch (JsonProcessingException exception) {
            log.warn("Could not create WebSocket notification", exception);
        }
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        clients.add(session);

        Mono<Void> incoming = session.receive().then();

        Mono<Void> outgoing = session.send(messageChannel.asFlux().map(session::textMessage));

        return Mono.firstWithSignal(incoming, outgoing).doFinally(signal -> clients.remove(session));

    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (exchange.getRequest().getMethod() != HttpMethod.POST) {
            return chain.filter(exchange);
        }

        String source = exchange.getRequest().getPath().value();

        String event = NOTIFICATION_EVENTS.get(source);

        if (event == null) {
            return chain.filter(exchange);
        }

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            HttpStatusCode status = exchange.getResponse().getStatusCode();

            if (status != null && status.is2xxSuccessful()) {
                broadcastNotification(event, source);
            }
        }));
    }
}
