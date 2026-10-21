package com.tanveer.wallet_service.service;

import com.tanveer.wallet_service.dto.DepositResult;

import java.util.UUID;

public interface DepositService {

    DepositResult deposit(UUID walletId, long amount, String idempotencyKey);
}
