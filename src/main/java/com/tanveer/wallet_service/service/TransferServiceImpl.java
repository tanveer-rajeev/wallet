package com.tanveer.wallet_service.service;

import com.tanveer.wallet_service.domain.*;
import com.tanveer.wallet_service.dto.TransferResult;
import com.tanveer.wallet_service.dto.TransferService;
import com.tanveer.wallet_service.exception.BusinessException;
import com.tanveer.wallet_service.repository.LedgerEntryRepository;
import com.tanveer.wallet_service.repository.OutboxEventRepository;
import com.tanveer.wallet_service.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private final IdempotencyGuard idempotencyGuard;
    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final OutboxEventRepository outboxEventRepository;

    @Override
    @Transactional
    public TransferResult transfer(UUID fromWalletId, UUID toWalletId, long amount, String idempotencyKey) {
        if (fromWalletId.equals(toWalletId)) {
            throw BusinessException.sameWalletTransfer();
        }
        long fee = FeePolicy.feeFor(amount);

        String requestHash = RequestHash.of(TransactionType.TRANSFER, fromWalletId, toWalletId, amount);
        IdempotencyGuard.Claim claim = idempotencyGuard.claim(TransactionType.TRANSFER, idempotencyKey, requestHash);
        TransferResult result = new TransferResult(
                claim.transactionId(), fromWalletId, toWalletId, amount, fee, claim.createdAt());
        if (claim.replay()) {
            return result;
        }

        Map<UUID, Wallet> wallets = lockInIdOrder(fromWalletId, toWalletId);
        Wallet sender = wallets.get(fromWalletId);
        Wallet receiver = wallets.get(toWalletId);

        requireCustomerWallet(sender);
        requireCustomerWallet(receiver);
        if (sender.getStatus() == WalletStatus.FROZEN) {
            throw BusinessException.walletFrozen(fromWalletId);
        }

        sender.debit(amount + fee);
        receiver.credit(amount);
        walletRepository.adjustBalance(SystemWallets.FEE, fee);

        saveLedger(result, amount + fee);
        saveTransferCompletedEvent(result);

        return result;
    }

    private Map<UUID, Wallet> lockInIdOrder(UUID firstId, UUID secondId) {
        Map<UUID, Wallet> locked = new HashMap<>();
        for (UUID id : new TreeSet<>(List.of(firstId, secondId))) {
            locked.put(id, walletRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> BusinessException.walletNotFound(id)));
        }
        return locked;
    }

    private void requireCustomerWallet(Wallet wallet) {
        if (wallet.getKind() != WalletKind.CUSTOMER) {
            throw BusinessException.systemWalletNotAllowed(wallet.getId());
        }
    }

    private void saveLedger(TransferResult r, long senderPays) {
        ledgerEntryRepository.saveAll(List.of(
                LedgerEntry.debit(r.transactionId(), r.fromWalletId(), senderPays, r.createdAt()),
                LedgerEntry.credit(r.transactionId(), r.toWalletId(), r.amount(), r.createdAt()),
                LedgerEntry.credit(r.transactionId(), SystemWallets.FEE, r.fee(), r.createdAt())));
    }


    private void saveTransferCompletedEvent(TransferResult r) {
        String payload = """
                {"transactionId":"%s","fromWalletId":"%s","toWalletId":"%s","amount":%d,"fee":%d}"""
                .formatted(r.transactionId(), r.fromWalletId(), r.toWalletId(), r.amount(), r.fee());
        outboxEventRepository.save(
                new OutboxEvent(r.transactionId(), OutboxEvent.TRANSFER_COMPLETED, payload, r.createdAt()));
    }
}
