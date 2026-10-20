package com.tanveer.wallet_service.domain;

public enum ErrorCode {
    WALLET_NOT_FOUND,
    INSUFFICIENT_FUNDS,
    WALLET_FROZEN,
    SYSTEM_WALLET_NOT_ALLOWED,
    SAME_WALLET_TRANSFER,
    IDEMPOTENCY_KEY_REUSED
}