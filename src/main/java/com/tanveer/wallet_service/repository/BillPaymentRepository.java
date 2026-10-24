package com.tanveer.wallet_service.repository;

import com.tanveer.wallet_service.domain.BillPayment;
import com.tanveer.wallet_service.domain.BillPaymentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillPaymentRepository extends JpaRepository<BillPayment, UUID> {

    @Query(value = "SELECT * FROM bill_payments WHERE id = :id FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<BillPayment> findByIdForUpdateSkipLocked(@Param("id") UUID id);

    Optional<BillPayment> findByHoldTransactionId(UUID holdTransactionId);

    List<BillPayment> findTop100ByStatusOrderByCreatedAt(BillPaymentStatus status);
}