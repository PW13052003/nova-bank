package com.novabank;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

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
            String token = jwtUtil.generateToken(userId);
            return ResponseEntity.ok(Map.of("userId", userId.toString(), "token", token));
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
            UUID userId = userService.login(body.get("email"), body.get("password"));
            if (userId == null) {
                return ResponseEntity.status(401).body(Map.of("error", "Invalid email or password"));
            }
            String token = jwtUtil.generateToken(userId);
            return ResponseEntity.ok(Map.of("userId", userId.toString(), "token", token));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(423).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Login failed"));
        }
    }
}