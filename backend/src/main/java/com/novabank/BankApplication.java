package com.novabank;

import java.util.UUID;

public class BankApplication {

    public static void main(String[] args) throws Exception {
        LedgerService ledgerService = new LedgerService();
        UUID checkingAccountId = UUID.fromString("6df565e6-8b22-4b78-b5e7-ac684b8f6423");
        UUID savingsAccountId = UUID.fromString("0a9f739c-1bac-41f1-9b57-a04916c1226d");

        System.out.println("Checking before: " + ledgerService.getBalance(checkingAccountId));
        System.out.println("Savings before: " + ledgerService.getBalance(savingsAccountId));

        UUID transferId = ledgerService.transfer(checkingAccountId, savingsAccountId, 2000, "transfer-test-001");
        System.out.println("Transfer id: " + transferId);

        System.out.println("Checking after: " + ledgerService.getBalance(checkingAccountId));
        System.out.println("Savings after: " + ledgerService.getBalance(savingsAccountId));
    }
}