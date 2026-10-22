package com.tanveer.wallet_service;

import com.jayway.jsonpath.JsonPath;
import com.tanveer.wallet_service.domain.ErrorCode;
import com.tanveer.wallet_service.domain.SystemWallets;
import com.tanveer.wallet_service.domain.Wallet;
import com.tanveer.wallet_service.dto.TransferService;
import com.tanveer.wallet_service.exception.BusinessException;
import com.tanveer.wallet_service.service.DepositService;
import com.tanveer.wallet_service.service.WalletService;
import com.tanveer.wallet_service.support.Parallel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class TransferConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    WalletService walletService;

    @Autowired
    DepositService depositService;

    @Autowired
    TransferService transferService;

    @Test
    void fiftyParallelTransfersFromA300000Wallet_exactly29Succeed() throws Exception {
        Wallet sender = walletService.createWallet("sender");
        Wallet receiver = walletService.createWallet("receiver");
        depositService.deposit(sender.getId(), 300_000, newKey());
        long feeWalletBefore = balanceOf(SystemWallets.FEE);

        List<Boolean> succeeded = Parallel.run(50, () -> {
            try {
                transferService.transfer(sender.getId(), receiver.getId(), 10_000, newKey());
                return true;
            } catch (BusinessException e) {
                assertThat(e.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS);
                return false;
            }
        });

        assertThat(succeeded.stream().filter(ok -> ok).count()).isEqualTo(29);
        assertThat(balanceOf(sender.getId())).isEqualTo(2_750);
        assertThat(balanceOf(receiver.getId())).isEqualTo(29 * 10_000);
        assertThat(balanceOf(SystemWallets.FEE) - feeWalletBefore).isEqualTo(29 * 250);
    }

    @Test
    void tenParallelRetriesOfTheSameTransfer() throws Exception {
        Wallet sender = walletService.createWallet("retry-sender");
        Wallet receiver = walletService.createWallet("retry-receiver");
        depositService.deposit(sender.getId(), 200_000, newKey());
        long feeWalletBefore = balanceOf(SystemWallets.FEE);
        String key = newKey();

        List<String> responses = Parallel.run(10, () -> mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"fromWalletId":"%s","toWalletId":"%s","amount":100000}"""
                                .formatted(sender.getId(), receiver.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        assertThat(responses.stream().distinct().count()).isEqualTo(1);
        assertThat(responses.stream().distinct().count()).isEqualTo(1);
        UUID transactionId = UUID.fromString(JsonPath.read(responses.get(0), "$.transactionId"));


        assertThat(count("SELECT count(*) FROM transactions WHERE idempotency_key = ?", key)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM ledger_entries WHERE transaction_id = ?", transactionId)).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM outbox_events WHERE aggregate_id = ?", transactionId)).isEqualTo(1);

        assertThat(balanceOf(sender.getId())).isEqualTo(200_000 - 100_500);
        assertThat(balanceOf(receiver.getId())).isEqualTo(100_000);
        assertThat(balanceOf(SystemWallets.FEE) - feeWalletBefore).isEqualTo(500);
    }

    private long count(String sql, Object param) {
        return jdbc.queryForObject(sql, Long.class, param);
    }
}
