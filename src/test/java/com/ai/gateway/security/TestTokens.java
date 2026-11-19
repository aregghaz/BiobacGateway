package com.ai.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/** Builds access tokens for tests, signed with a test-only secret. */
final class TestTokens {

    static final String SECRET = "gateway-test-secret-0123456789-abcdefghijk";

    private TestTokens() {
    }

    static String valid(long userId) {
        return signed(SECRET, userId, Instant.now().plusSeconds(600));
    }

    static String expired(long userId) {
        return signed(SECRET, userId, Instant.now().minusSeconds(60));
    }

    static String signedWithAnotherKey(long userId) {
        return signed("another-secret-0123456789-abcdefghijklmnop", userId, Instant.now().plusSeconds(600));
    }

    private static String signed(String secret, long userId, Instant expiresAt) {
        return Jwts.builder()
                .setSubject("user" + userId)
                .claim("userId", userId)
                .setIssuedAt(new Date())
                .setExpiration(Date.from(expiresAt))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }
}
