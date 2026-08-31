package com.novabank;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
public class TransactionController {

    private final LedgerService ledgerService;
    private final AccountService accountService;

    public TransactionController(LedgerService ledgerService, AccountService accountService) {
        this.ledgerService = ledgerService;
        this.accountService = accountService;
    }

    @PostMapping("/deposit")
    public ResponseEntity<?> deposit(@RequestBody Map<String, Object> body, Authentication authentication) {
        try {
            UUID userId = (UUID) authentication.getPrincipal();
            UUID accountId = UUID.fromString((String) body.get("accountId"));
            long amountCents = Long.parseLong(body.get("amountCents").toString());
            String idempotencyKey = (String) body.get("idempotencyKey");

            if (!accountService.isOwnedBy(accountId, userId)) {
                return ResponseEntity.status(403).body(Map.of("error", "You do not own this account"));
            }

            UUID transactionId = ledgerService.deposit(accountId, amountCents, idempotencyKey);
            return ResponseEntity.ok(Map.of("transactionId", transactionId.toString()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Deposit failed"));
        }
    }
}