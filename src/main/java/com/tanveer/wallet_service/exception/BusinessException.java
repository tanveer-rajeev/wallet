package com.tanveer.wallet_service.exception;

import com.tanveer.wallet_service.domain.BillPaymentStatus;
import com.tanveer.wallet_service.domain.ErrorCode;
import com.tanveer.wallet_service.domain.WalletKind;
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

    public static BusinessException invalidAmount(long amount) {
        return new BusinessException(ErrorCode.INVALID_AMOUNT, "amount must be positive: " + amount);
    }

    public static BusinessException idempotencyKeyReused() {
        return new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED,
                "This Idempotency-Key was already used with a different request");
    }

    public static BusinessException idempotencyKeyRemoved(String key) {
        return new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REMOVED,
                "Idempotency row vanished for key: " + key);
    }

    public static BusinessException invalidIdempotencyKey() {
        return new BusinessException(ErrorCode.INVALID_IDEMPOTENCY_KEY,
                "Idempotency-Key header is required (1-100 characters)");
    }

    public static BusinessException missingAccount(WalletKind kind) {
        return new BusinessException(ErrorCode.MISSING_ACCOUNT, kind.name() + " account is missing");
    }

    public static BusinessException billPaymentNotFound(UUID transactionId) {
        return new BusinessException(
                ErrorCode.BILL_PAYMENT_NOT_FOUND,
                "No bill payment found for transaction " + transactionId
        );
    }

    public static BusinessException billPaymentAlreadyRefunded(UUID paymentId) {
        return new BusinessException(
                ErrorCode.BILL_PAYMENT_ALREADY_REFUNDED,
                "Bill payment " + paymentId + " was already refunded"
        );
    }

    public static BusinessException billPaymentNotPending(UUID paymentId, BillPaymentStatus status) {
        return new BusinessException(
                ErrorCode.BILL_PAYMENT_NOT_PENDING,
                "Bill payment " + paymentId + " is already " + status
        );
    }
}
