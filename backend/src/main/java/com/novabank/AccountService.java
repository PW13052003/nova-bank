package com.novabank;
import org.springframework.stereotype.Service;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class AccountService {

    public UUID createAccount(UUID userId, String accountType) throws SQLException, java.io.IOException {
        if (!accountType.equals("CHECKING") && !accountType.equals("SAVINGS")) {
            throw new IllegalArgumentException("Account type must be CHECKING or SAVINGS");
        }

        String accountNumber = generateAccountNumber();

        String sql = "INSERT INTO accounts (user_id, account_number, account_type) VALUES (?, ?, ?) RETURNING id";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            stmt.setString(2, accountNumber);
            stmt.setString(3, accountType);

            ResultSet rs = stmt.executeQuery();
            rs.next();
            return (UUID) rs.getObject("id");
        }
    }

    private String generateAccountNumber() {
        long number = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L);
        return String.valueOf(number);
    }

    public List<Map<String, Object>> getAccountsForUser(UUID userId) throws SQLException, java.io.IOException {
        String sql = "SELECT id, account_number, account_type, status FROM accounts WHERE user_id = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            ResultSet rs = stmt.executeQuery();

            List<Map<String, Object>> accounts = new java.util.ArrayList<>();
            while (rs.next()) {
                accounts.add(Map.of(
                        "id", rs.getObject("id").toString(),
                        "accountNumber", rs.getString("account_number"),
                        "accountType", rs.getString("account_type"),
                        "status", rs.getString("status")
                ));
            }
            return accounts;
        }
    }

    public boolean isOwnedBy(UUID accountId, UUID userId) throws SQLException, java.io.IOException {
        String sql = "SELECT 1 FROM accounts WHERE id = ? AND user_id = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, accountId);
            stmt.setObject(2, userId);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        }
    }
}