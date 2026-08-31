package com.novabank;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class LedgerService {

    public long getBalance(UUID accountId) throws SQLException, java.io.IOException {
        String sql = "SELECT COALESCE(SUM(amount_cents), 0) AS balance FROM ledger_entries WHERE account_id = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, accountId);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getLong("balance");
        }
    }

    public UUID deposit(UUID accountId, long amountCents, String idempotencyKey) throws SQLException, java.io.IOException {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);

            try {
                String txnSql = "INSERT INTO transactions (idempotency_key, type, status, description) " +
                        "VALUES (?, 'DEPOSIT', 'COMPLETED', 'Deposit') RETURNING id";
                UUID transactionId;
                try (PreparedStatement txnStmt = conn.prepareStatement(txnSql)) {
                    txnStmt.setString(1, idempotencyKey);
                    ResultSet txnRs = txnStmt.executeQuery();
                    txnRs.next();
                    transactionId = (UUID) txnRs.getObject("id");
                }

                long currentBalance = getBalanceWithinTransaction(conn, accountId);
                long newBalance = currentBalance + amountCents;

                String ledgerSql = "INSERT INTO ledger_entries (transaction_id, account_id, amount_cents, balance_after) " +
                        "VALUES (?, ?, ?, ?)";
                try (PreparedStatement ledgerStmt = conn.prepareStatement(ledgerSql)) {
                    ledgerStmt.setObject(1, transactionId);
                    ledgerStmt.setObject(2, accountId);
                    ledgerStmt.setLong(3, amountCents);
                    ledgerStmt.setLong(4, newBalance);
                    ledgerStmt.executeUpdate();
                }

                conn.commit();
                return transactionId;

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private long getBalanceWithinTransaction(Connection conn, UUID accountId) throws SQLException {
        String sql = "SELECT COALESCE(SUM(amount_cents), 0) AS balance FROM ledger_entries WHERE account_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, accountId);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getLong("balance");
        }
    }

    public UUID withdraw(UUID accountId, long amountCents, String idempotencyKey) throws SQLException, java.io.IOException {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);

            try {
                long currentBalance = getBalanceWithinTransaction(conn, accountId);

                if (currentBalance < amountCents) {
                    conn.rollback();
                    throw new IllegalStateException("Insufficient balance");
                }

                String txnSql = "INSERT INTO transactions (idempotency_key, type, status, description) " +
                        "VALUES (?, 'WITHDRAWAL', 'COMPLETED', 'Withdrawal') RETURNING id";
                UUID transactionId;
                try (PreparedStatement txnStmt = conn.prepareStatement(txnSql)) {
                    txnStmt.setString(1, idempotencyKey);
                    ResultSet txnRs = txnStmt.executeQuery();
                    txnRs.next();
                    transactionId = (UUID) txnRs.getObject("id");
                }

                long newBalance = currentBalance - amountCents;

                String ledgerSql = "INSERT INTO ledger_entries (transaction_id, account_id, amount_cents, balance_after) " +
                        "VALUES (?, ?, ?, ?)";
                try (PreparedStatement ledgerStmt = conn.prepareStatement(ledgerSql)) {
                    ledgerStmt.setObject(1, transactionId);
                    ledgerStmt.setObject(2, accountId);
                    ledgerStmt.setLong(3, -amountCents);
                    ledgerStmt.setLong(4, newBalance);
                    ledgerStmt.executeUpdate();
                }

                conn.commit();
                return transactionId;

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private void lockAccount(Connection conn, UUID accountId) throws SQLException {
        String sql = "SELECT id FROM accounts WHERE id = ? FOR UPDATE";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setObject(1, accountId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                throw new SQLException("Account not found: " + accountId);
            }
        }
    }

    public UUID transfer(UUID fromAccountId, UUID toAccountId, long amountCents, String idempotencyKey)
            throws SQLException, java.io.IOException {

        if (amountCents <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);

            try {
                UUID firstLock = fromAccountId.compareTo(toAccountId) < 0 ? fromAccountId : toAccountId;
                UUID secondLock = fromAccountId.compareTo(toAccountId) < 0 ? toAccountId : fromAccountId;

                lockAccount(conn, firstLock);
                lockAccount(conn, secondLock);

                long fromBalance = getBalanceWithinTransaction(conn, fromAccountId);

                if (fromBalance < amountCents) {
                    conn.rollback();
                    throw new IllegalStateException("Insufficient balance");
                }

                long toBalance = getBalanceWithinTransaction(conn, toAccountId);

                String txnSql = "INSERT INTO transactions (idempotency_key, type, status, description) " +
                        "VALUES (?, 'TRANSFER', 'COMPLETED', 'Transfer') RETURNING id";
                UUID transactionId;
                try (PreparedStatement txnStmt = conn.prepareStatement(txnSql)) {
                    txnStmt.setString(1, idempotencyKey);
                    ResultSet txnRs = txnStmt.executeQuery();
                    txnRs.next();
                    transactionId = (UUID) txnRs.getObject("id");
                }

                long newFromBalance = fromBalance - amountCents;
                long newToBalance = toBalance + amountCents;

                String ledgerSql = "INSERT INTO ledger_entries (transaction_id, account_id, amount_cents, balance_after) " +
                        "VALUES (?, ?, ?, ?)";
                try (PreparedStatement ledgerStmt = conn.prepareStatement(ledgerSql)) {
                    ledgerStmt.setObject(1, transactionId);
                    ledgerStmt.setObject(2, fromAccountId);
                    ledgerStmt.setLong(3, -amountCents);
                    ledgerStmt.setLong(4, newFromBalance);
                    ledgerStmt.executeUpdate();

                    ledgerStmt.setObject(1, transactionId);
                    ledgerStmt.setObject(2, toAccountId);
                    ledgerStmt.setLong(3, amountCents);
                    ledgerStmt.setLong(4, newToBalance);
                    ledgerStmt.executeUpdate();
                }

                conn.commit();
                return transactionId;

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }
}