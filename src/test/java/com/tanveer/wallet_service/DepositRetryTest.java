package com.tanveer.wallet_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tanveer.wallet_service.domain.Wallet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.tanveer.wallet_service.dto.DepositResult;
import com.tanveer.wallet_service.service.DepositService;
import com.tanveer.wallet_service.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DepositRetryTest extends AbstractIntegrationTest {

    @Autowired
    DepositService depositService;

    @Autowired
    WalletService walletService;

    @Test
    void tenParallelRetriesOfTheSameDepositMoveMoneyOnce() throws Exception {
        Wallet wallet = walletService.createWallet("retry-user");
        String key = UUID.randomUUID().toString();
        int threads = 10;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<DepositResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();                       // release all threads at the same moment
                return depositService.deposit(wallet.getId(), 100_000, key);
            }));
        }
        start.countDown();

        Set<UUID> transactionIds = new HashSet<>();
        for (Future<DepositResult> f : futures) {
            transactionIds.add(f.get(30, TimeUnit.SECONDS).transactionId());
        }
        pool.shutdown();

        assertThat(transactionIds).as("all 10 calls report the same transaction").hasSize(1);
        assertThat(jdbc.queryForObject("SELECT balance FROM wallets WHERE id = ?", Long.class, wallet.getId()))
                .isEqualTo(100_000);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM transactions WHERE idempotency_key = ?", Long.class, key)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM ledger_entries WHERE transaction_id = ?", Long.class,
                transactionIds.iterator().next())).isEqualTo(2);
    }
}
