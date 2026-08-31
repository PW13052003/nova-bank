package com.novabank;

import java.util.UUID;

public class BankApplication {

    public static void main(String[] args) throws Exception {
        LedgerService ledgerService = new LedgerService();
        UUID checkingAccountId = UUID.fromString("6df565e6-8b22-4b78-b5e7-ac684b8f6423");

        UUID withdrawalId = ledgerService.withdraw(checkingAccountId, 3000, "withdraw-test-001");
        System.out.println("Withdrawal transaction id: " + withdrawalId);
        System.out.println("Balance after withdrawal: " + ledgerService.getBalance(checkingAccountId) + " cents");

        try {
            ledgerService.withdraw(checkingAccountId, 999999, "withdraw-test-002");
            System.out.println("Overdraft succeeded (this should NOT happen)");
        } catch (IllegalStateException e) {
            System.out.println("Overdraft correctly rejected: " + e.getMessage());
        }

        System.out.println("Final balance: " + ledgerService.getBalance(checkingAccountId) + " cents");
    }
}