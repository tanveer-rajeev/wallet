package com.tanveer.wallet_service.dto;

import java.time.Instant;
import java.util.UUID;

public record DepositResponse(UUID transactionId, UUID walletId, long amount, Instant createdAt) {

    public static DepositResponse from(DepositResult r) {
        return new DepositResponse(r.transactionId(), r.walletId(), r.amount(), r.createdAt());
    }
}
