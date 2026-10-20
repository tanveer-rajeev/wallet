package com.tanveer.wallet_service;

import com.tanveer.wallet_service.domain.Wallet;
import com.tanveer.wallet_service.domain.WalletKind;
import com.tanveer.wallet_service.domain.WalletStatus;
import com.tanveer.wallet_service.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(TestcontainersConfig.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WalletPersistenceTest{

    @Autowired
    private WalletRepository repository;

    @Autowired
    private TestEntityManager testEm;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void systemWalletsAreSeededByMigration() {
        List<Wallet> system = repository.findByOwnerId("SYSTEM");

        assertThat(system)
                .extracting(Wallet::getKind)
                .containsExactlyInAnyOrder(
                        WalletKind.FEE, WalletKind.CASH_IN, WalletKind.BILLER_CLEARING);
        assertThat(system).allSatisfy(w -> {
            assertThat(w.getCurrency()).isEqualTo("IQD");
            assertThat(w.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        });
    }

    @Test
    void customerWalletRoundTrips() {
        Wallet saved = repository.saveAndFlush(Wallet.newCustomerWallet("user-1"));
        testEm.clear();

        Wallet loaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getOwnerId()).isEqualTo("user-1");
        assertThat(loaded.getKind()).isEqualTo(WalletKind.CUSTOMER);
        assertThat(loaded.getBalance()).isZero();
        assertThat(loaded.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void databaseRejectsNegativeCustomerBalance() {
        Wallet saved = repository.saveAndFlush(Wallet.newCustomerWallet("user-2"));

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE wallets SET balance = -1 WHERE id = ?", saved.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_customer_balance_non_negative");
    }
}