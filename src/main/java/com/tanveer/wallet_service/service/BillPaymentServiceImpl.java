package com.tanveer.wallet_service.service;

import com.tanveer.wallet_service.domain.BillPayment;
import com.tanveer.wallet_service.domain.BillPaymentStatus;
import com.tanveer.wallet_service.repository.BillPaymentRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BillPaymentServiceImpl implements BillPaymentService {

    private final BillPaymentProcessor processor;
    private final BillPaymentRepository billPaymentRepository;

    @Override
    public BillPayment pay(UUID walletId, long amount, String idempotencyKey) {
        BillPayment payment = processor.hold(walletId, amount, idempotencyKey);
        processor.settle(payment.getId());
        return billPaymentRepository.findById(payment.getId()).orElseThrow();
    }

    @Override
    public void settleOpenPayments() {
        billPaymentRepository.findTop100ByStatusOrderByCreatedAt(BillPaymentStatus.PENDING)
                .forEach(payment -> processor.settle(payment.getId()));
    }
}