package com.tanveer.wallet_service.dto;

import java.time.Instant;
import java.util.UUID;

public record TransferResponse(UUID transactionId, UUID fromWalletId, UUID toWalletId,
                               long amount, long fee, Instant createdAt) {

    public static TransferResponse from(TransferResult r) {
        return new TransferResponse(
                r.transactionId(), r.fromWalletId(), r.toWalletId(), r.amount(), r.fee(), r.createdAt());
    }
}
