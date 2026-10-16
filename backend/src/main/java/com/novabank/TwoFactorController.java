package com.novabank;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/2fa")
public class TwoFactorController {

    private final UserService userService;

    public TwoFactorController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/setup")
    public ResponseEntity<?> setup(Authentication authentication) {
        try {
            UUID userId = (UUID) authentication.getPrincipal();
            UserService.TwoFactorSetup setup = userService.beginTwoFactorSetup(userId);
            return ResponseEntity.ok(Map.of("secret", setup.secret(), "otpAuthUri", setup.otpAuthUri()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Setup failed"));
        }
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(@RequestBody Map<String, String> body, Authentication authentication) {
        try {
            UUID userId = (UUID) authentication.getPrincipal();
            userService.confirmTwoFactorSetup(userId, body.get("password"), body.get("code"));
            return ResponseEntity.ok(Map.of("enabled", true));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Confirmation failed"));
        }
    }
}