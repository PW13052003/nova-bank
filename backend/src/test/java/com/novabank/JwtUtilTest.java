package com.novabank;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtUtilTest {

    private static final String SECRET = "unit-test-secret-that-is-comfortably-longer-than-thirty-two-bytes";
    private static final String OTHER_SECRET = "another-unit-test-secret-that-is-also-longer-than-thirty-two-bytes";

    private final JwtUtil jwt = new JwtUtil(SECRET);
    private final UUID userId = UUID.randomUUID();

    @Test
    void sessionTokenRoundTrips() {
        assertEquals(userId, jwt.validateAndGetUserId(jwt.generateToken(userId)));
    }

    @Test
    void twoFactorTokenRoundTrips() {
        assertEquals(userId, jwt.validateTwoFactorToken(jwt.generateTwoFactorToken(userId)));
    }

    @Test
    void twoFactorTokenCannotBeUsedAsSession() {
        String token = jwt.generateTwoFactorToken(userId);
        assertThrows(JwtException.class, () -> jwt.validateAndGetUserId(token));
    }

    @Test
    void sessionTokenCannotBeUsedForTwoFactor() {
        String token = jwt.generateToken(userId);
        assertThrows(JwtException.class, () -> jwt.validateTwoFactorToken(token));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = new JwtUtil(OTHER_SECRET).generateToken(userId);
        assertThrows(JwtException.class, () -> jwt.validateAndGetUserId(token));
    }

    @Test
    void expiredTokenIsRejected() {
        long now = System.currentTimeMillis();
        String token = Jwts.builder()
                .subject(userId.toString())
                .claim("scope", "session")
                .issuedAt(new Date(now - 120_000))
                .expiration(new Date(now - 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .compact();
        assertThrows(JwtException.class, () -> jwt.validateAndGetUserId(token));
    }

    @Test
    void tokenWithoutScopeIsRejected() {
        long now = System.currentTimeMillis();
        String token = Jwts.builder()
                .subject(userId.toString())
                .issuedAt(new Date(now))
                .expiration(new Date(now + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes()))
                .compact();
        assertThrows(JwtException.class, () -> jwt.validateAndGetUserId(token));
    }

    @Test
    void garbageIsRejected() {
        assertThrows(JwtException.class, () -> jwt.validateAndGetUserId("not.a.token"));
    }
}