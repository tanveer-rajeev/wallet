package com.tanveer.wallet_service.service;

import com.tanveer.wallet_service.domain.*;
import com.tanveer.wallet_service.dto.DepositResult;
import com.tanveer.wallet_service.exception.BusinessException;
import com.tanveer.wallet_service.repository.LedgerEntryRepository;
import com.tanveer.wallet_service.repository.WalletRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DepositServiceImpl implements DepositService {

    private final IdempotencyGuard idempotencyGuard;
    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    @Override
    @Transactional
    public DepositResult deposit(UUID walletId, long amount, String idempotencyKey) {
        String requestHash = RequestHash.of(TransactionType.DEPOSIT, walletId, amount);
        IdempotencyGuard.Claim claim = idempotencyGuard.claim(TransactionType.DEPOSIT, idempotencyKey, requestHash);
        if (claim.replay()) {
            return new DepositResult(claim.transactionId(), walletId, amount, claim.createdAt());
        }

        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> BusinessException.walletNotFound(walletId));
        if (wallet.getKind() != WalletKind.CUSTOMER) {
            throw BusinessException.systemWalletNotAllowed(walletId);
        }

        UUID txId = claim.transactionId();
        Instant at = claim.createdAt();
        ledgerEntryRepository.saveAll(List.of(
                LedgerEntry.debit(txId, SystemWallets.CASH_IN, amount, at),
                LedgerEntry.credit(txId, walletId, amount, at)));

        if (walletRepository.adjustBalance(SystemWallets.CASH_IN, -amount) != 1) {
            throw BusinessException.missingAccount(WalletKind.CASH_IN);
        }
        wallet.credit(amount);

        return new DepositResult(txId, walletId, amount, at);
    }
}
