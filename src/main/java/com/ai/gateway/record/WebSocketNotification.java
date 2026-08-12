package com.ai.gateway.record;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record WebSocketNotification(
        String channel,
        String event,
        Map<String, Object> identifiers,
        Set<Long> recipientUserIds,
        Instant timestamp
) {}
