package com.tanveer.wallet_service.dto;

import com.tanveer.wallet_service.domain.BillPayment;
import com.tanveer.wallet_service.domain.BillPaymentStatus;
import java.time.Instant;
import java.util.UUID;

public record BillPaymentResponse(UUID id, UUID walletId, long amount, BillPaymentStatus status, Instant createdAt) {

    public static BillPaymentResponse from(BillPayment p) {
        return new BillPaymentResponse(p.getId(), p.getWalletId(), p.getAmount(), p.getStatus(), p.getCreatedAt());
    }
}
