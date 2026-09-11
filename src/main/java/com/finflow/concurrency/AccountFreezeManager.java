package com.finflow.concurrency;

import com.finflow.entity.Wallet;
import com.finflow.enums.WalletStatus;
import com.finflow.exception.ResourceNotFoundException;
import com.finflow.repository.WalletRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class AccountFreezeManager {

    private static final Logger log = LoggerFactory.getLogger(AccountFreezeManager.class);

    private final ConcurrentHashMap<Long, FreezeRecord> frozenAccounts = new ConcurrentHashMap<>();
    private final ReentrantLock freezeLock = new ReentrantLock(true);
    private final WalletRepository walletRepository;

    public AccountFreezeManager(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @PostConstruct
    public void restoreFrozenWallets() {
        walletRepository.findByStatus(WalletStatus.FROZEN).forEach(wallet -> {
            LocalDateTime frozenAt = wallet.getFrozenAt() == null
                    ? wallet.getUpdatedAt()
                    : wallet.getFrozenAt();
            frozenAccounts.put(wallet.getId(), new FreezeRecord(
                    wallet.getId(),
                    wallet.getFreezeReason(),
                    frozenAt));
        });

        if (!frozenAccounts.isEmpty()) {
            log.info("[FREEZE] Restored {} frozen wallet records", frozenAccounts.size());
        }
    }

    @Transactional
    public void freezeWallet(Long walletId, String reason) {
        freezeLock.lock();
        try {
            Wallet wallet = walletRepository.findByIdWithPessimisticLock(walletId)
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));
            LocalDateTime frozenAt = LocalDateTime.now();
            wallet.setStatus(WalletStatus.FROZEN);
            wallet.setFreezeReason(reason);
            wallet.setFrozenAt(frozenAt);
            wallet.setUpdatedAt(frozenAt);
            walletRepository.saveAndFlush(wallet);

            frozenAccounts.put(walletId, new FreezeRecord(walletId, reason, frozenAt));
            log.warn("[FREEZE] Wallet {} frozen. Reason: {}", walletId, reason);
        } finally {
            freezeLock.unlock();
        }
    }

    @Transactional
    public void unfreezeWallet(Long walletId) {
        freezeLock.lock();
        try {
            Wallet wallet = walletRepository.findByIdWithPessimisticLock(walletId)
                    .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));
            wallet.setStatus(WalletStatus.ACTIVE);
            wallet.setFreezeReason(null);
            wallet.setFrozenAt(null);
            wallet.setUpdatedAt(LocalDateTime.now());
            walletRepository.saveAndFlush(wallet);

            frozenAccounts.remove(walletId);
            log.info("[FREEZE] Wallet {} unfrozen.", walletId);
        } finally {
            freezeLock.unlock();
        }
    }

    public boolean isFrozen(Long walletId) {
        return frozenAccounts.containsKey(walletId);
    }

    public String getFreezeReason(Long walletId) {
        FreezeRecord record = frozenAccounts.get(walletId);
        return record != null ? record.reason() : null;
    }

    public int getFrozenCount() {
        return frozenAccounts.size();
    }

    public record FreezeRecord(Long walletId, String reason, LocalDateTime frozenAt) {
    }
}
