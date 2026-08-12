package com.ai.gateway.service;

import com.ai.gateway.record.WebSocketNotification;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Set;

@Service
public class WebSocketNotificationPublisher {

    private final Sinks.Many<WebSocketNotification> channel = Sinks.many().multicast().directBestEffort();

    public void publish(WebSocketNotification notification) {
        Sinks.EmitResult result = channel.tryEmitNext(notification);

        if (result != Sinks.EmitResult.OK
                && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
            throw new IllegalStateException(
                    "Could not publish WebSocket message: " + result
            );
        }
    }

    public Flux<WebSocketNotification> messagesFor(Long connectedUserId) {
        return channel.asFlux()
                .filter(notification -> {
                    Set<Long> recipients = notification.recipientUserIds();
                    return recipients != null && recipients.contains(connectedUserId);
                });
    }

}
