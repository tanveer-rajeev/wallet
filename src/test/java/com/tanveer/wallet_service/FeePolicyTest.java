package com.tanveer.wallet_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tanveer.wallet_service.domain.FeePolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class FeePolicyTest {

    @ParameterizedTest(name = "amount {0} -> fee {1}")
    @CsvSource({
            "1,        250",
            "10000,    250",
            "49999,    250",
            "50000,    250",
            "50099,    250",
            "50100,    251",
            "50299,    251",
            "50300,    252",
            "100000,   500",
            "1000000,  5000",
            "1999899,  9999",
            "1999900,  10000",
            "2000000,  10000",
            "2000001,  10000",
            "2500000,  10000",
            "5000000,  10000"
    })
    void calculatesFee(long amount, long expectedFee) {
        assertThat(FeePolicy.feeFor(amount)).isEqualTo(expectedFee);
    }

    @Test
    void senderPaysAmountPlusFee() {
        assertThat(FeePolicy.totalDebit(100_000)).isEqualTo(100_500);
        assertThat(FeePolicy.totalDebit(10_000)).isEqualTo(10_250);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -5000})
    void rejectsNonPositiveAmounts(long amount) {
        assertThatThrownBy(() -> FeePolicy.feeFor(amount))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
