package com.finflow.service;

import com.finflow.entity.LedgerEntry;
import com.finflow.entity.Transaction;
import com.finflow.entity.Wallet;
import com.finflow.enums.EntryType;
import com.finflow.repository.LedgerEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerService(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional
    public void createDebitEntry(Transaction transaction, Wallet wallet, BigDecimal amount, BigDecimal balanceAfter) {
        LedgerEntry entry = new LedgerEntry();
        entry.setTransaction(transaction);
        entry.setWallet(wallet);
        entry.setEntryType(EntryType.DEBIT);
        entry.setAmount(amount);
        entry.setBalanceAfter(balanceAfter);
        ledgerEntryRepository.save(entry);
    }

    @Transactional
    public void createCreditEntry(Transaction transaction, Wallet wallet, BigDecimal amount, BigDecimal balanceAfter) {
        LedgerEntry entry = new LedgerEntry();
        entry.setTransaction(transaction);
        entry.setWallet(wallet);
        entry.setEntryType(EntryType.CREDIT);
        entry.setAmount(amount);
        entry.setBalanceAfter(balanceAfter);
        ledgerEntryRepository.save(entry);
    }
}