package com.finflow.repository;

import com.finflow.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByWallet_IdOrderByCreatedAtDesc(Long walletId);

    List<LedgerEntry> findByTransaction_IdOrderByCreatedAtAsc(Long transactionId);
}