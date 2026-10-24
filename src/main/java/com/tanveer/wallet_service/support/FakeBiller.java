package com.tanveer.wallet_service.support;

import com.tanveer.wallet_service.biller.BillerClient;
import com.tanveer.wallet_service.biller.BillerDecision;
import com.tanveer.wallet_service.biller.BillerTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class FakeBiller implements BillerClient {

    public enum Status { PAID, NOT_PAID }

    private final Random random;
    private final Map<String, Long> paid = new HashMap<>();

    public FakeBiller(long seed) {
        this.random = new Random(seed);
    }

    @Override
    public synchronized BillerDecision pay(String reference, long amount) {
        if (paid.containsKey(reference)) {
            return BillerDecision.APPROVED;
        }
        int roll = random.nextInt(100);
        if (roll < 60) {
            paid.put(reference, amount);
            return BillerDecision.APPROVED;
        }
        if (roll < 75) {
            return BillerDecision.DECLINED;
        }
        if (roll < 90) {
            paid.put(reference, amount);
            throw new BillerTimeoutException("timeout after paying " + reference);
        }
        throw new BillerTimeoutException("timeout before paying " + reference);
    }

    public synchronized Status status(String reference) {
        return paid.containsKey(reference) ? Status.PAID : Status.NOT_PAID;
    }

    public synchronized long totalReceived() {
        return paid.values().stream().mapToLong(Long::longValue).sum();
    }

    public synchronized int paidCount() {
        return paid.size();
    }
}
