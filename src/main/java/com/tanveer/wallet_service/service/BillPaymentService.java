package com.tanveer.wallet_service.service;

import com.tanveer.wallet_service.domain.BillPayment;
import java.util.UUID;

public interface BillPaymentService {
    BillPayment pay(UUID walletId, long amount, String idempotencyKey);
    void settleOpenPayments();
}
