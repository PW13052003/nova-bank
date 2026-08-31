package com.novabank;

import java.util.UUID;

public class BankApplication {

    public static void main(String[] args) throws Exception {
        LedgerService ledgerService = new LedgerService();
        UUID checkingAccountId = UUID.fromString("6df565e6-8b22-4b78-b5e7-ac684b8f6423");

        try {
            ledgerService.deposit(checkingAccountId, 10000, "deposit-test-001");
            System.out.println("Deposit succeeded (this should NOT happen)");
        } catch (java.sql.SQLException e) {
            System.out.println("Deposit correctly rejected: " + e.getMessage());
        }

        long balance = ledgerService.getBalance(checkingAccountId);
        System.out.println("Balance after retry attempt: " + balance + " cents");
    }
}