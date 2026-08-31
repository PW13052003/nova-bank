package com.novabank;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class BankApplication {

    public static void main(String[] args) throws Exception {
        LedgerService ledgerService = new LedgerService();
        UUID checkingAccountId = UUID.fromString("6df565e6-8b22-4b78-b5e7-ac684b8f6423");
        UUID savingsAccountId = UUID.fromString("0a9f739c-1bac-41f1-9b57-a04916c1226d");

        System.out.println("Checking before: " + ledgerService.getBalance(checkingAccountId));
        System.out.println("Savings before: " + ledgerService.getBalance(savingsAccountId));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            int threadNum = i;
            boolean checkingToSavings = threadNum % 2 == 0;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (checkingToSavings) {
                        ledgerService.transfer(checkingAccountId, savingsAccountId, 1000, "concurrent-transfer-cs-" + threadNum);
                    } else {
                        ledgerService.transfer(savingsAccountId, checkingAccountId, 1000, "concurrent-transfer-sc-" + threadNum);
                    }
                    successes.incrementAndGet();
                } catch (IllegalStateException e) {
                    failures.incrementAndGet();
                } catch (Exception e) {
                    System.out.println("Unexpected error: " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        long checkingAfter = ledgerService.getBalance(checkingAccountId);
        long savingsAfter = ledgerService.getBalance(savingsAccountId);

        System.out.println("Successful transfers: " + successes.get());
        System.out.println("Rejected transfers: " + failures.get());
        System.out.println("Checking after: " + checkingAfter);
        System.out.println("Savings after: " + savingsAfter);
        System.out.println("Total (should always be 10000): " + (checkingAfter + savingsAfter));
    }
}