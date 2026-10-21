package com.tanveer.wallet_service.repository;

import com.tanveer.wallet_service.domain.Wallet;
import com.tanveer.wallet_service.domain.WalletKind;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    List<Wallet> findByOwnerId(String ownerId);
    Wallet findByKind(WalletKind kind);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.id = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") UUID id);

    @Modifying
    @Query("update Wallet w set w.balance = w.balance + :amount where w.id = :id")
    int adjustBalance(@Param("id") UUID id, @Param("amount") long amount);
}
