package com.tanveer.wallet_service.exception;

import com.tanveer.wallet_service.domain.ErrorCode;
import lombok.Getter;

import java.util.UUID;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode code;

    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public static BusinessException walletNotFound(UUID id) {
        return new BusinessException(ErrorCode.WALLET_NOT_FOUND, "Wallet not found: " + id);
    }

    public static BusinessException insufficientFunds(UUID walletId) {
        return new BusinessException(ErrorCode.INSUFFICIENT_FUNDS,
                "Wallet " + walletId + " has insufficient funds");
    }

    public static BusinessException walletFrozen(UUID walletId) {
        return new BusinessException(ErrorCode.WALLET_FROZEN, "Wallet " + walletId + " is frozen");
    }

    public static BusinessException systemWalletNotAllowed(UUID walletId) {
        return new BusinessException(ErrorCode.SYSTEM_WALLET_NOT_ALLOWED,
                "Wallet " + walletId + " cannot be used by customers");
    }

    public static BusinessException sameWalletTransfer() {
        return new BusinessException(ErrorCode.SAME_WALLET_TRANSFER,
                "Sender and receiver must be different wallets");
    }

    public static BusinessException idempotencyKeyReused() {
        return new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                "This Idempotency-Key was already used with a different request");
    }
}
