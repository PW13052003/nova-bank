package com.novabank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerServiceTest extends DatabaseTestBase {

    private final UserService userService = new UserService();
    private final AccountService accountService = new AccountService();
    private final LedgerService ledgerService = new LedgerService();

    private UUID checking;
    private UUID savings;

    @BeforeEach
    void createAccounts() throws Exception {
        UUID userId = userService.signup("ledger@example.com", "SecurePass123", "Ledger User");
        checking = accountService.createAccount(userId, "CHECKING");
        savings = accountService.createAccount(userId, "SAVINGS");
    }

    @Test
    void depositIncreasesBalance() throws Exception {
        ledgerService.deposit(checking, 7500, key());
        assertEquals(7500, ledgerService.getBalance(checking));
    }

    @Test
    void withdrawalReducesBalance() throws Exception {
        ledgerService.deposit(checking, 5000, key());
        ledgerService.withdraw(checking, 1800, key());
        assertEquals(3200, ledgerService.getBalance(checking));
    }

    @Test
    void withdrawalBeyondBalanceIsRejectedWithoutSideEffects() throws Exception {
        ledgerService.deposit(checking, 1000, key());
        assertThrows(IllegalStateException.class, () -> ledgerService.withdraw(checking, 5000, key()));
        assertEquals(1000, ledgerService.getBalance(checking));
        assertEquals(0, queryLong("SELECT COUNT(*) FROM transactions WHERE type = 'WITHDRAWAL'"));
    }

    @Test
    void nonPositiveAmountsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ledgerService.deposit(checking, 0, key()));
        assertThrows(IllegalArgumentException.class, () -> ledgerService.withdraw(checking, -5, key()));
        assertThrows(IllegalArgumentException.class, () -> ledgerService.transfer(checking, savings, 0, key()));
    }

    @Test
    void duplicateIdempotencyKeyDoesNotMoveMoneyTwice() throws Exception {
        String repeated = key();
        ledgerService.deposit(checking, 1000, repeated);
        assertThrows(SQLException.class, () -> ledgerService.deposit(checking, 1000, repeated));
        assertEquals(1000, ledgerService.getBalance(checking));
    }

    @Test
    void transferMovesMoneyAndLedgerEntriesSumToZero() throws Exception {
        ledgerService.deposit(checking, 5000, key());
        ledgerService.transfer(checking, savings, 2000, key());
        assertEquals(3000, ledgerService.getBalance(checking));
        assertEquals(2000, ledgerService.getBalance(savings));
        assertEquals(2, transferEntryCount());
        assertEquals(0, transferLedgerSum());
    }

    @Test
    void transferToSameAccountIsRejected() throws Exception {
        ledgerService.deposit(checking, 5000, key());
        assertThrows(IllegalArgumentException.class, () -> ledgerService.transfer(checking, checking, 100, key()));
        assertEquals(5000, ledgerService.getBalance(checking));
    }

    @Test
    void transferBeyondBalanceIsRejectedWithoutSideEffects() throws Exception {
        ledgerService.deposit(checking, 1000, key());
        assertThrows(IllegalStateException.class, () -> ledgerService.transfer(checking, savings, 5000, key()));
        assertEquals(1000, ledgerService.getBalance(checking));
        assertEquals(0, ledgerService.getBalance(savings));
        assertEquals(0, transferEntryCount());
    }

    @Test
    void concurrentWithdrawalsNeverOverdraw() throws Exception {
        ledgerService.deposit(checking, 5000, key());

        Outcome outcome = runConcurrently(10, i -> ledgerService.withdraw(checking, 1000, key()));

        assertTrue(outcome.finished());
        assertEquals(0, outcome.unexpected());
        assertEquals(5, outcome.succeeded());
        assertEquals(5, outcome.rejected());
        assertEquals(0, ledgerService.getBalance(checking));
    }

    @Test
    void oppositeTransfersRunSimultaneouslyWithoutDeadlockOrLostMoney() throws Exception {
        ledgerService.deposit(checking, 5000, key());
        ledgerService.deposit(savings, 5000, key());

        Outcome outcome = runConcurrently(10, i -> {
            if (i % 2 == 0) {
                ledgerService.transfer(checking, savings, 1000, key());
            } else {
                ledgerService.transfer(savings, checking, 1000, key());
            }
        });

        assertTrue(outcome.finished());
        assertEquals(0, outcome.unexpected());
        assertEquals(10, outcome.succeeded());
        assertEquals(10000, ledgerService.getBalance(checking) + ledgerService.getBalance(savings));
        assertEquals(0, transferLedgerSum());
    }

    private String key() {
        return UUID.randomUUID().toString();
    }

    private long transferLedgerSum() throws Exception {
        return queryLong("SELECT COALESCE(SUM(l.amount_cents), 0) FROM ledger_entries l "
                + "JOIN transactions t ON t.id = l.transaction_id WHERE t.type = 'TRANSFER'");
    }

    private long transferEntryCount() throws Exception {
        return queryLong("SELECT COUNT(*) FROM ledger_entries l "
                + "JOIN transactions t ON t.id = l.transaction_id WHERE t.type = 'TRANSFER'");
    }

    private long queryLong(String sql) throws Exception {
        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private record Outcome(int succeeded, int rejected, int unexpected, boolean finished) {
    }

    private interface Task {
        void run(int index) throws Exception;
    }

    private Outcome runConcurrently(int count, Task task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(count);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        for (int i = 0; i < count; i++) {
            int index = i;
            executor.submit(() -> {
                try {
                    start.await();
                    task.run(index);
                    succeeded.incrementAndGet();
                } catch (IllegalStateException e) {
                    rejected.incrementAndGet();
                } catch (Exception e) {
                    unexpected.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        boolean finished = done.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();
        return new Outcome(succeeded.get(), rejected.get(), unexpected.get(), finished);
    }
}