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

        System.out.println("Balance before: " + ledgerService.getBalance(checkingAccountId));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successes = new AtomicInteger(0);
        AtomicInteger failures = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            int threadNum = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    ledgerService.withdraw(checkingAccountId, 1000, "concurrent-test-round2-" + threadNum);
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

        System.out.println("Successful withdrawals: " + successes.get());
        System.out.println("Rejected withdrawals: " + failures.get());
        System.out.println("Balance after: " + ledgerService.getBalance(checkingAccountId));
    }
}