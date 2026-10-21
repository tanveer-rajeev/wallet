package com.tanveer.wallet_service.dto;

import java.time.Instant;
import java.util.UUID;

public record DepositResult(UUID transactionId, UUID walletId, long amount, Instant createdAt) {
}
