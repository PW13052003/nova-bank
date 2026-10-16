package com.novabank;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    private static final String SCOPE_CLAIM = "scope";
    private static final String SESSION_SCOPE = "session";
    private static final String TWO_FACTOR_SCOPE = "2fa";
    private static final long SESSION_MS = 15 * 60 * 1000;
    private static final long TWO_FACTOR_MS = 5 * 60 * 1000;

    private final SecretKey key;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(UUID userId) {
        return build(userId, SESSION_SCOPE, SESSION_MS);
    }

    public String generateTwoFactorToken(UUID userId) {
        return build(userId, TWO_FACTOR_SCOPE, TWO_FACTOR_MS);
    }

    public UUID validateAndGetUserId(String token) {
        return parse(token, SESSION_SCOPE);
    }

    public UUID validateTwoFactorToken(String token) {
        return parse(token, TWO_FACTOR_SCOPE);
    }

    private String build(UUID userId, String scope, long lifetimeMs) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(SCOPE_CLAIM, scope)
                .issuedAt(new Date(now))
                .expiration(new Date(now + lifetimeMs))
                .signWith(key)
                .compact();
    }

    private UUID parse(String token, String expectedScope) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!expectedScope.equals(claims.get(SCOPE_CLAIM, String.class))) {
            throw new JwtException("Unexpected token scope");
        }
        return UUID.fromString(claims.getSubject());
    }
}