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

    @PostMapping("/withdraw")
    public ResponseEntity<?> withdraw(@RequestBody Map<String, Object> body, Authentication authentication) {
        try {
            UUID userId = (UUID) authentication.getPrincipal();
            UUID accountId = UUID.fromString((String) body.get("accountId"));
            long amountCents = Long.parseLong(body.get("amountCents").toString());
            String idempotencyKey = (String) body.get("idempotencyKey");

            if (!accountService.isOwnedBy(accountId, userId)) {
                return ResponseEntity.status(403).body(Map.of("error", "You do not own this account"));
            }

            UUID transactionId = ledgerService.withdraw(accountId, amountCents, idempotencyKey);
            return ResponseEntity.ok(Map.of("transactionId", transactionId.toString()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Withdrawal failed"));
        }
    }

    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(@RequestBody Map<String, Object> body, Authentication authentication) {
        try {
            UUID userId = (UUID) authentication.getPrincipal();
            UUID fromAccountId = UUID.fromString((String) body.get("fromAccountId"));
            UUID toAccountId = UUID.fromString((String) body.get("toAccountId"));
            long amountCents = Long.parseLong(body.get("amountCents").toString());
            String idempotencyKey = (String) body.get("idempotencyKey");

            if (!accountService.isOwnedBy(fromAccountId, userId)) {
                return ResponseEntity.status(403).body(Map.of("error", "You do not own the source account"));
            }

            UUID transactionId = ledgerService.transfer(fromAccountId, toAccountId, amountCents, idempotencyKey);
            return ResponseEntity.ok(Map.of("transactionId", transactionId.toString()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Transfer failed"));
        }
    }

    @GetMapping("/accounts/{accountId}/transactions")
    public ResponseEntity<?> getTransactionHistory(@PathVariable String accountId, Authentication authentication) {
        try {
            UUID userId = (UUID) authentication.getPrincipal();
            UUID accountUuid = UUID.fromString(accountId);

            if (!accountService.isOwnedBy(accountUuid, userId)) {
                return ResponseEntity.status(403).body(Map.of("error", "You do not own this account"));
            }

            return ResponseEntity.ok(ledgerService.getTransactionHistory(accountUuid));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid account ID"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to fetch transaction history"));
        }
    }
}