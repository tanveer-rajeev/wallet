package com.tanveer.wallet_service.domain;

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
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "ledger_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;

    @Column(nullable = false)
    private long amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private LedgerEntry(UUID transactionId, UUID walletId, Direction direction, long amount, Instant createdAt) {
        this.transactionId = transactionId;
        this.walletId = walletId;
        this.direction = direction;
        this.amount = amount;
        this.createdAt = createdAt;
    }

    public static LedgerEntry debit(UUID transactionId, UUID walletId, long amount, Instant at) {
        return new LedgerEntry(transactionId, walletId, Direction.DEBIT, amount, at);
    }

    public static LedgerEntry credit(UUID transactionId, UUID walletId, long amount, Instant at) {
        return new LedgerEntry(transactionId, walletId, Direction.CREDIT, amount, at);
    }
}
