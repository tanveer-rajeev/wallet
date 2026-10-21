package com.tanveer.wallet_service.domain;

import com.tanveer.wallet_service.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Transfer fee rule: 0.5% of the amount, rounded HALF_UP to whole dinars,
 * never less than 250 IQD and never more than 10,000 IQD.
 */
public final class FeePolicy {

    public static final long MIN_FEE = 250;
    public static final long MAX_FEE = 10_000;
    private static final BigDecimal FEE_RATE = new BigDecimal("0.005");

    private FeePolicy() {
    }

    public static long feeFor(long amount) {
        if (amount <= 0) {
            throw BusinessException.invalidAmount(amount);
        }
        long rawFee = BigDecimal.valueOf(amount)
                .multiply(FEE_RATE)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        return Math.min(MAX_FEE, Math.max(MIN_FEE, rawFee));
    }

    public static long totalDebit(long amount) {
        return amount + feeFor(amount);
    }
}
