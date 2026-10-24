package com.tanveer.wallet_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class BillPaymentReconciler {

    private final BillPaymentService billPaymentService;

    @Scheduled(fixedDelayString = "${bill-payments.reconcile-interval-ms:30000}")
    public void run() {
        billPaymentService.settleOpenPayments();
    }
}
