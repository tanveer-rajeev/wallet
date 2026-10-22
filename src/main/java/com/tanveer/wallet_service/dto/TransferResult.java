package com.tanveer.wallet_service.dto;
import java.time.Instant;
import java.util.UUID;

public record TransferResult(UUID transactionId, UUID fromWalletId, UUID toWalletId,
                             long amount, long fee, Instant createdAt) {
}
