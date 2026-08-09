package com.ai.biobacgateway.controller;

import com.ai.biobacgateway.record.WebSocketNotification;
import com.ai.biobacgateway.service.WebSocketNotificationPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/ws")
public class InternalWebSocketController {
    private final WebSocketNotificationPublisher publisher;

    public InternalWebSocketController(WebSocketNotificationPublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping("/publish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void publish(@RequestBody WebSocketNotification notification) {
        publisher.publish(notification);
    }
}
