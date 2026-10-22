package com.tanveer.wallet_service.domain;
import com.tanveer.wallet_service.dto.DepositRequest;
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

import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "wallets")
public class Wallet {

    public static final String CURRENCY_IQD = "IQD";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletKind kind;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private long balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static Wallet newCustomerWallet(String ownerId) {
        Wallet wallet = new Wallet();
        wallet.ownerId = ownerId;
        wallet.kind = WalletKind.CUSTOMER;
        wallet.currency = CURRENCY_IQD;
        wallet.balance = 0;
        wallet.status = WalletStatus.ACTIVE;
        wallet.createdAt = Instant.now();
        return wallet;
    }

    public void credit(long amount) {
        if (amount <= 0) {
            throw BusinessException.invalidAmount(amount);
        }
        this.balance += amount;
    }

    public void debit(long amount) {
        if (amount <= 0) {
            throw BusinessException.invalidAmount(amount);
        }
        if (balance < amount) {
            throw BusinessException.insufficientFunds(id);
        }
        this.balance -= amount;
    }
}
