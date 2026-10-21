package com.tanveer.wallet_service.service;

import com.tanveer.wallet_service.domain.Transaction;
import com.tanveer.wallet_service.domain.TransactionType;
import com.tanveer.wallet_service.exception.BusinessException;
import com.tanveer.wallet_service.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Makes an operation run at most once per Idempotency-Key.
 * Must be called as the FIRST step of the caller's transaction: the claim row commits or rolls back
 * together with the money movement, so a crash can never leave "key used, money not moved".
 */
@Component
@RequiredArgsConstructor
public class IdempotencyGuard {

    private static final int MAX_KEY_LENGTH = 100;

    private final TransactionRepository transactions;

    public record Claim(UUID transactionId, Instant createdAt, boolean replay) {
    }

    public Claim claim(TransactionType type, String key, String requestHash) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw BusinessException.invalidIdempotencyKey();
        }

        UUID id = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

        if (transactions.insertIfAbsent(id, type.name(), key, requestHash, now) == 1) {
            return new Claim(id, now, false);
        }

        Transaction existing = transactions.findByTypeAndIdempotencyKey(type, key)
                .orElseThrow(() -> BusinessException.idempotencyKeyRemoved(key));
        if (!existing.getRequestHash().equals(requestHash)) {
            throw BusinessException.idempotencyKeyReused();
        }
        return new Claim(existing.getId(), existing.getCreatedAt(), true);
    }
}
