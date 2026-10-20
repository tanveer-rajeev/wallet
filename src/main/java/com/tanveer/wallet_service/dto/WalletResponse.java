package com.tanveer.wallet_service.dto;

import com.tanveer.wallet_service.domain.Wallet;
import com.tanveer.wallet_service.domain.WalletStatus;

import java.time.Instant;
import java.util.UUID;

/** What the API shows about a wallet. The entity is never serialized directly. */
public record WalletResponse(
        UUID id,
        String ownerId,
        String currency,
        long balance,
        WalletStatus status,
        Instant createdAt
) {
    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getOwnerId(),
                wallet.getCurrency(),
                wallet.getBalance(),
                wallet.getStatus(),
                wallet.getCreatedAt());
    }
}
