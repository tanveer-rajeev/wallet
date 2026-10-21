package com.tanveer.wallet_service.repository;

import com.tanveer.wallet_service.domain.Transaction;
import com.tanveer.wallet_service.domain.TransactionType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO transactions (id, type, idempotency_key, request_hash, created_at)
            VALUES (:id, :type, :idempotencyKey, :requestHash, :createdAt)
            ON CONFLICT (type, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("type") String type,
                       @Param("idempotencyKey") String idempotencyKey,
                       @Param("requestHash") String requestHash,
                       @Param("createdAt") Instant createdAt);

    Optional<Transaction> findByTypeAndIdempotencyKey(TransactionType type, String idempotencyKey);
}
