package com.novabank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LedgerAuditTest extends DatabaseTestBase {

    private final UserService userService = new UserService();
    private final AccountService accountService = new AccountService();
    private final LedgerService ledgerService = new LedgerService();

    private UUID userId;
    private UUID checking;
    private UUID savings;

    @BeforeEach
    void createAccounts() throws Exception {
        userId = userService.signup("audit@example.com", "SecurePass123", "Audit User");
        checking = accountService.createAccount(userId, "CHECKING");
        savings = accountService.createAccount(userId, "SAVINGS");
    }

    @Test
    void depositWritesOneAuditRow() throws Exception {
        UUID transactionId = ledgerService.deposit(checking, 7500, key());

        assertEquals(1, auditCount("TRANSACTION_DEPOSIT"));
        assertEquals(userId.toString(), auditField("TRANSACTION_DEPOSIT", "user_id::text"));
        assertEquals(transactionId.toString(), auditField("TRANSACTION_DEPOSIT", "metadata->>'transactionId'"));
        assertEquals(checking.toString(), auditField("TRANSACTION_DEPOSIT", "metadata->>'accountId'"));
        assertEquals("7500", auditField("TRANSACTION_DEPOSIT", "metadata->>'amountCents'"));
    }

    @Test
    void withdrawalWritesOneAuditRow() throws Exception {
        ledgerService.deposit(checking, 5000, key());
        UUID transactionId = ledgerService.withdraw(checking, 1800, key());

        assertEquals(1, auditCount("TRANSACTION_WITHDRAWAL"));
        assertEquals(userId.toString(), auditField("TRANSACTION_WITHDRAWAL", "user_id::text"));
        assertEquals(transactionId.toString(), auditField("TRANSACTION_WITHDRAWAL", "metadata->>'transactionId'"));
        assertEquals("1800", auditField("TRANSACTION_WITHDRAWAL", "metadata->>'amountCents'"));
    }

    @Test
    void transferWritesOneAuditRowWithBothAccounts() throws Exception {
        ledgerService.deposit(checking, 5000, key());
        UUID transactionId = ledgerService.transfer(checking, savings, 2000, key());

        assertEquals(1, auditCount("TRANSACTION_TRANSFER"));
        assertEquals(userId.toString(), auditField("TRANSACTION_TRANSFER", "user_id::text"));
        assertEquals(transactionId.toString(), auditField("TRANSACTION_TRANSFER", "metadata->>'transactionId'"));
        assertEquals(checking.toString(), auditField("TRANSACTION_TRANSFER", "metadata->>'fromAccountId'"));
        assertEquals(savings.toString(), auditField("TRANSACTION_TRANSFER", "metadata->>'toAccountId'"));
        assertEquals("2000", auditField("TRANSACTION_TRANSFER", "metadata->>'amountCents'"));
    }

    @Test
    void rejectedWithdrawalLeavesNoAuditRow() throws Exception {
        ledgerService.deposit(checking, 1000, key());
        assertThrows(IllegalStateException.class, () -> ledgerService.withdraw(checking, 5000, key()));
        assertEquals(0, auditCount("TRANSACTION_WITHDRAWAL"));
    }

    @Test
    void rejectedTransferLeavesNoAuditRow() throws Exception {
        ledgerService.deposit(checking, 1000, key());
        assertThrows(IllegalStateException.class, () -> ledgerService.transfer(checking, savings, 5000, key()));
        assertEquals(0, auditCount("TRANSACTION_TRANSFER"));
    }

    @Test
    void duplicateIdempotencyKeyDoesNotWriteASecondAuditRow() throws Exception {
        String repeated = key();
        ledgerService.deposit(checking, 1000, repeated);
        assertThrows(SQLException.class, () -> ledgerService.deposit(checking, 1000, repeated));
        assertEquals(1, auditCount("TRANSACTION_DEPOSIT"));
    }

    private String key() {
        return UUID.randomUUID().toString();
    }

    private long auditCount(String eventType) throws Exception {
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM audit_log WHERE event_type = ?")) {
            stmt.setString(1, eventType);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getLong(1);
        }
    }

    private String auditField(String eventType, String expression) throws Exception {
        String sql = "SELECT " + expression + " FROM audit_log WHERE event_type = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, eventType);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getString(1);
        }
    }
}