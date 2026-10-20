package com.tanveer.wallet_service.repository;

import com.tanveer.wallet_service.domain.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    List<Wallet> findByOwnerId(String ownerId);
}
