package com.novabank;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    public ResponseEntity<?> createAccount(@RequestBody Map<String, String> body, Authentication authentication) {
        try {
            UUID userId = (UUID) authentication.getPrincipal();
            String accountType = body.get("accountType");
            UUID accountId = accountService.createAccount(userId, accountType);
            return ResponseEntity.ok(Map.of("accountId", accountId.toString()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Account creation failed"));
        }
    }

    @GetMapping
    public ResponseEntity<?> listAccounts(Authentication authentication) {
        try {
            UUID userId = (UUID) authentication.getPrincipal();
            return ResponseEntity.ok(accountService.getAccountsForUser(userId));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to fetch accounts"));
        }
    }
}