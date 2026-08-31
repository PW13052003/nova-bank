package com.novabank;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
public class ProtectedController {

    @GetMapping("/me")
    public Map<String, String> me(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return Map.of("authenticatedUserId", userId.toString());
    }
}