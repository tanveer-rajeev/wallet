package com.tanveer.wallet_service;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
public abstract class AbstractIntegrationTest {
    @Autowired
    protected JdbcTemplate jdbc;

    @AfterEach
    void booksMustBalance() {
        Long balances = jdbc.queryForObject(
                "SELECT COALESCE(SUM(balance), 0) FROM wallets", Long.class);
        assertThat(balances).isZero();

        Long creditsMinusDebits = jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END), 0)
                FROM ledger_entries
                """, Long.class);
        assertThat(creditsMinusDebits).isZero();

        List<String> mismatched = jdbc.queryForList("""
                SELECT w.id::text FROM wallets w
                WHERE w.balance <> (
                    SELECT COALESCE(SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END), 0)
                    FROM ledger_entries WHERE wallet_id = w.id)
                """, String.class);
        assertThat(mismatched).isEmpty();
    }
}
