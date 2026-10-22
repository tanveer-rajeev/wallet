package com.tanveer.wallet_service.dto;

import java.util.UUID;

public interface TransferService {

    TransferResult transfer(UUID fromWalletId, UUID toWalletId, long amount, String idempotencyKey);
}
