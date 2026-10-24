package com.tanveer.wallet_service.service;

import com.tanveer.wallet_service.biller.BillerClient;
import com.tanveer.wallet_service.biller.BillerDecision;
import com.tanveer.wallet_service.biller.BillerTimeoutException;
import com.tanveer.wallet_service.domain.BillPayment;
import com.tanveer.wallet_service.domain.BillPaymentStatus;
import com.tanveer.wallet_service.domain.LedgerEntry;
import com.tanveer.wallet_service.domain.SystemWallets;
import com.tanveer.wallet_service.domain.TransactionType;
import com.tanveer.wallet_service.domain.Wallet;
import com.tanveer.wallet_service.exception.BusinessException;
import com.tanveer.wallet_service.repository.BillPaymentRepository;
import com.tanveer.wallet_service.repository.LedgerEntryRepository;
import com.tanveer.wallet_service.repository.WalletRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The two database steps of a bill payment. Each runs in its own transaction. */
@Slf4j
@Component
@RequiredArgsConstructor
public class BillPaymentProcessor {

    private final IdempotencyGuard idempotencyGuard;
    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final BillPaymentRepository billPaymentRepository;
    private final BillerClient biller;

    @Transactional
    public BillPayment hold(UUID walletId, long amount, String idempotencyKey) {
        String requestHash = RequestHash.of(TransactionType.BILL_HOLD, walletId, amount);
        IdempotencyGuard.Claim claim = idempotencyGuard.claim(TransactionType.BILL_HOLD, idempotencyKey, requestHash);
        if (claim.replay()) {
            return billPaymentRepository.findByHoldTransactionId(claim.transactionId())
                    .orElseThrow(() -> BusinessException.billPaymentNotFound(claim.transactionId()));
        }

        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> BusinessException.walletNotFound(walletId));
        wallet.requireCustomer();
        wallet.requireNotFrozen();

        wallet.debit(amount);
        walletRepository.adjustBalance(SystemWallets.BILLER_CLEARING, amount);
        ledgerEntryRepository.saveAll(List.of(
                LedgerEntry.debit(claim.transactionId(), walletId, amount, claim.createdAt()),
                LedgerEntry.credit(claim.transactionId(), SystemWallets.BILLER_CLEARING, amount, claim.createdAt())));

        return billPaymentRepository.save(
                new BillPayment(walletId, amount, claim.transactionId(), claim.createdAt()));
    }

    @Transactional
    public void settle(UUID paymentId) {
        Optional<BillPayment> locked = billPaymentRepository.findByIdForUpdateSkipLocked(paymentId);
        if (locked.isEmpty() || locked.get().getStatus() != BillPaymentStatus.PENDING) {
            return;
        }
        BillPayment payment = locked.get();

        BillerDecision decision;
        try {
            decision = biller.pay(payment.getId().toString(), payment.getAmount());
        } catch (BillerTimeoutException e) {
            log.warn("Biller outcome unknown for payment {}: {}", payment.getId(), e.getMessage());
            return;
        }

        if (decision == BillerDecision.APPROVED) {
            payment.markSucceeded();
        } else {
            UUID refundTransactionId = refund(payment);
            payment.markFailed(refundTransactionId);
        }
    }

    private UUID refund(BillPayment payment) {
        UUID paymentId = payment.getId();
        String requestHash = RequestHash.of(TransactionType.BILL_REFUND, paymentId);
        IdempotencyGuard.Claim claim =
                idempotencyGuard.claim(TransactionType.BILL_REFUND, paymentId.toString(), requestHash);
        if (claim.replay()) {
            throw BusinessException.billPaymentAlreadyRefunded(paymentId);
        }

        Wallet wallet = walletRepository.findByIdForUpdate(payment.getWalletId())
                .orElseThrow(() -> BusinessException.walletNotFound(payment.getWalletId()));

        wallet.credit(payment.getAmount());
        walletRepository.adjustBalance(SystemWallets.BILLER_CLEARING, -payment.getAmount());
        ledgerEntryRepository.saveAll(List.of(
                LedgerEntry.debit(claim.transactionId(), SystemWallets.BILLER_CLEARING, payment.getAmount(), claim.createdAt()),
                LedgerEntry.credit(claim.transactionId(), payment.getWalletId(), payment.getAmount(), claim.createdAt())));

        return claim.transactionId();
    }
}