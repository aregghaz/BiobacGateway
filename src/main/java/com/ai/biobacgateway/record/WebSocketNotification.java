package com.ai.biobacgateway.record;

import java.time.Instant;
import java.util.Map;

public record WebSocketNotification(
        String channel,
        String event,
        Map<String, Object> identifiers,
        Instant timestamp
) {}
