package com.tanveer.wallet_service.repository;

import com.tanveer.wallet_service.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
}
