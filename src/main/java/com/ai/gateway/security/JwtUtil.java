package com.ai.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;

/**
 * Verifies access tokens issued by BioBacUser. Refresh tokens are signed with a different key, so they fail here.
 */
@Component
public class JwtUtil {

    static final String TOKEN_TYPE_CLAIM = "typ";
    static final String ACCESS_TOKEN_TYPE = "access";

    private final Key key;

    public JwtUtil(@Value("${jwt.secret}") String jwtSecret) {
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @throws TokenRejectedException with {@link TokenRejectedException.Reason#EXPIRED} or
     *                                {@link TokenRejectedException.Reason#INVALID}
     */
    public VerifiedToken verify(String token) {
        Claims claims;
        try {
            claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException ex) {
            throw new TokenRejectedException(TokenRejectedException.Reason.EXPIRED);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new TokenRejectedException(TokenRejectedException.Reason.INVALID);
        }
        Long userId = toUserId(claims.get("userId"));
        // tokens issued before the type claim existed have none and stay valid until they expire
        Object type = claims.get(TOKEN_TYPE_CLAIM);
        boolean notAnAccessToken = type != null && !ACCESS_TOKEN_TYPE.equals(type);
        if (userId == null || claims.getExpiration() == null || notAnAccessToken) {
            throw new TokenRejectedException(TokenRejectedException.Reason.INVALID);
        }
        return new VerifiedToken(userId, claims.getExpiration().toInstant());
    }

    private Long toUserId(Object userId) {
        if (userId instanceof Number number) {
            return number.longValue();
        }
        if (userId instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    public record VerifiedToken(Long userId, Instant expiresAt) {
    }
}
