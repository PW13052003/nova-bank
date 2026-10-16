package com.novabank;

import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String SESSION_COOKIE = "token";
    private static final String TWO_FACTOR_COOKIE = "twofa";
    private static final long SESSION_SECONDS = 15 * 60;
    private static final long TWO_FACTOR_SECONDS = 5 * 60;

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public AuthController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody Map<String, String> body) {
        try {
            UUID userId = userService.signup(body.get("email"), body.get("password"), body.get("fullName"));
            return sessionResponse(userId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Signup failed"));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        try {
            UserService.LoginResult result = userService.authenticate(body.get("email"), body.get("password"));
            if (result == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid email or password"));
            }
            if (result.twoFactorRequired()) {
                String token = jwtUtil.generateTwoFactorToken(result.userId());
                return ResponseEntity.ok()
                        .header(HttpHeaders.SET_COOKIE, cookie(TWO_FACTOR_COOKIE, token, TWO_FACTOR_SECONDS).toString())
                        .body(Map.of("twoFactorRequired", true));
            }
            return sessionResponse(result.userId());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(423).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Login failed"));
        }
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<?> verifyTwoFactor(@RequestBody Map<String, String> body,
                                             @CookieValue(name = TWO_FACTOR_COOKIE, required = false) String twoFactorToken) {
        if (twoFactorToken == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Please log in again"));
        }

        UUID userId;
        try {
            userId = jwtUtil.validateTwoFactorToken(twoFactorToken);
        } catch (JwtException | IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("error", "Please log in again"));
        }

        try {
            if (!userService.verifyLoginCode(userId, body.get("code"))) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid code"));
            }
            return sessionResponse(userId);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(423).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Verification failed"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(SESSION_COOKIE, "", 0).toString())
                .header(HttpHeaders.SET_COOKIE, cookie(TWO_FACTOR_COOKIE, "", 0).toString())
                .body(Map.of("message", "Logged out"));
    }

    private ResponseEntity<?> sessionResponse(UUID userId) {
        String token = jwtUtil.generateToken(userId);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(SESSION_COOKIE, token, SESSION_SECONDS).toString())
                .header(HttpHeaders.SET_COOKIE, cookie(TWO_FACTOR_COOKIE, "", 0).toString())
                .body(Map.of("userId", userId.toString(), "twoFactorRequired", false));
    }

    private static ResponseCookie cookie(String name, String value, long maxAgeSeconds) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("None")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build();
    }
}