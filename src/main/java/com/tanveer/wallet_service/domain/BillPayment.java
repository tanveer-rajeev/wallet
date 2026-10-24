package com.tanveer.wallet_service.domain;

import com.tanveer.wallet_service.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "bill_payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BillPaymentStatus status;

    @Column(name = "hold_transaction_id", nullable = false)
    private UUID holdTransactionId;

    @Column(name = "refund_transaction_id")
    private UUID refundTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public BillPayment(UUID walletId, long amount, UUID holdTransactionId, Instant createdAt) {
        this.walletId = walletId;
        this.amount = amount;
        this.holdTransactionId = holdTransactionId;
        this.createdAt = createdAt;
        this.status = BillPaymentStatus.PENDING;
    }

    /** Only a PENDING payment can be finished, and only once. */
    public void markSucceeded() {
        requirePending();
        this.status = BillPaymentStatus.SUCCEEDED;
    }

    public void markFailed(UUID refundTransactionId) {
        requirePending();
        this.refundTransactionId = refundTransactionId;
        this.status = BillPaymentStatus.FAILED;
    }

    private void requirePending() {
        if (status != BillPaymentStatus.PENDING) {
            throw BusinessException.billPaymentNotPending(id,BillPaymentStatus.PENDING);
        }
    }
}
