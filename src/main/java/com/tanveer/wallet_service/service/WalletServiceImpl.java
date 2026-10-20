package com.tanveer.wallet_service.service;

import com.tanveer.wallet_service.domain.Wallet;
import com.tanveer.wallet_service.exception.BusinessException;
import com.tanveer.wallet_service.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@RequiredArgsConstructor
@Service
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;

    @Override
    @Transactional
    public Wallet createWallet(String ownerId) {
        return walletRepository.save(Wallet.newCustomerWallet(ownerId));
    }

    @Override
    @Transactional(readOnly = true)
    public Wallet getWallet(UUID id) {
        return walletRepository.findById(id).orElseThrow(() -> BusinessException.walletNotFound(id));
    }
}
