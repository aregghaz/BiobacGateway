package com.ai.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;

@Component
public class JwtUtil {
    private final Key key;

    public JwtUtil(
            @Value("${jwt.secret}") String jwtSecret
    ) {
        this.key = Keys.hmacShaKeyFor(
                jwtSecret.getBytes(StandardCharsets.UTF_8)
        );
    }

    public Long validateAndExtractUserId(String token) {
        Jws<Claims> parsedToken = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token);

        Object userId = parsedToken
                .getBody()
                .get("userId");

        if (userId == null) {
            throw new IllegalArgumentException("Token does not contain userId");
        }

        if (userId instanceof Number number) {
            return number.longValue();
        }

        return Long.parseLong(userId.toString());
    }
}
