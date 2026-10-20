package com.tanveer.wallet_service.service;

import com.tanveer.wallet_service.domain.Wallet;

import java.util.UUID;

public interface WalletService {

    Wallet createWallet(String ownerId);
    Wallet getWallet(UUID id);
}
