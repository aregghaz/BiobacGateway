package com.ai.gateway.security;

import com.ai.gateway.security.JwtUtil.VerifiedToken;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Accepts a WebSocket handshake only from an allowed admin-UI origin with a valid access token. Browsers always send
 * {@code Origin} on WebSocket handshakes, so a missing one is rejected too.
 */
@Component
public class WebSocketHandshakeValidator {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AllowedOrigins allowedOrigins;
    private final JwtUtil jwtUtil;

    public WebSocketHandshakeValidator(AllowedOrigins allowedOrigins, JwtUtil jwtUtil) {
        this.allowedOrigins = allowedOrigins;
        this.jwtUtil = jwtUtil;
    }

    /**
     * @throws TokenRejectedException when the origin isn't allowed (reported as INVALID) or the token is missing,
     *                                expired or invalid
     */
    public VerifiedToken validate(HandshakeInfo handshake) {
        if (!allowedOrigins.isAllowed(handshake.getHeaders().getOrigin())) {
            throw new TokenRejectedException(TokenRejectedException.Reason.INVALID);
        }
        String token = UriComponentsBuilder.fromUri(handshake.getUri()).build().getQueryParams().getFirst("token");
        if (token == null || token.isBlank()) {
            throw new TokenRejectedException(TokenRejectedException.Reason.MISSING);
        }
        // the UI URL-encodes the token; a raw "Bearer " prefix is tolerated as before
        String decoded = URLDecoder.decode(token, StandardCharsets.UTF_8);
        if (decoded.startsWith(BEARER_PREFIX)) {
            decoded = decoded.substring(BEARER_PREFIX.length());
        }
        return jwtUtil.verify(decoded);
    }
}
