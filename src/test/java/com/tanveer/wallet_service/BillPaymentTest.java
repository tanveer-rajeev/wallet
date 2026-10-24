package com.tanveer.wallet_service;

import static org.assertj.core.api.Assertions.assertThat;

import com.tanveer.wallet_service.domain.Wallet;
import com.tanveer.wallet_service.service.BillPaymentService;
import com.tanveer.wallet_service.service.DepositService;
import com.tanveer.wallet_service.service.WalletService;
import com.tanveer.wallet_service.support.FakeBiller;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BillPaymentTest extends AbstractIntegrationTest {

    private static final long FUNDING = 2_000_000;

    @Autowired
    WalletService walletService;

    @Autowired
    DepositService depositService;

    @Autowired
    BillPaymentService billPaymentService;

    @Autowired
    FakeBiller biller;

    @Test
    void twoHundredPaymentsAllSettleAndMatchWhatTheBillerReceived() {
        Wallet wallet = walletService.createWallet("bill-payer");
        UUID walletId = wallet.getId();
        depositService.deposit(walletId, FUNDING, newKey());

        for (int i = 0; i < 200; i++) {
            billPaymentService.pay(walletId, 1000 + i, newKey());
        }

        for (int round = 0; round < 50 && countByStatus(walletId, "PENDING") > 0; round++) {
            billPaymentService.settleOpenPayments();
        }

        long succeeded = countByStatus(walletId, "SUCCEEDED");
        long failed = countByStatus(walletId, "FAILED");
        assertThat(countByStatus(walletId, "PENDING")).isZero();
        assertThat(succeeded + failed).isEqualTo(200);

        jdbc.queryForList("SELECT id, status FROM bill_payments WHERE wallet_id = ?", walletId)
                .forEach(row -> assertThat(biller.status(row.get("id").toString()) == FakeBiller.Status.PAID)
                        .isEqualTo(row.get("status").equals("SUCCEEDED")));

        assertThat(FUNDING - balanceOf(walletId)).isEqualTo(biller.totalReceived());

        assertThat(biller.paidCount()).isEqualTo(succeeded);
        assertThat(jdbc.queryForObject(
                "SELECT count(DISTINCT refund_transaction_id) FROM bill_payments WHERE wallet_id = ?",
                Long.class, walletId)).isEqualTo(failed);
    }

    private long countByStatus(UUID walletId, String status) {
        return jdbc.queryForObject("SELECT count(*) FROM bill_payments WHERE wallet_id = ? AND status = ?",
                Long.class, walletId, status);
    }
}
