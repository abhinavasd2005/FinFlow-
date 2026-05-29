package com.finflow.concurrency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class AccountFreezeManager {

    private static final Logger log = LoggerFactory.getLogger(AccountFreezeManager.class);

    private final ConcurrentHashMap<Long, FreezeRecord> frozenAccounts = new ConcurrentHashMap<>();
    private final ReentrantLock freezeLock = new ReentrantLock(true);

    public void freezeWallet(Long walletId, String reason) {
        freezeLock.lock();
        try {
            frozenAccounts.put(walletId, new FreezeRecord(walletId, reason, LocalDateTime.now()));
            log.warn("[FREEZE] Wallet {} frozen. Reason: {}", walletId, reason);
        } finally {
            freezeLock.unlock();
        }
    }

    public void unfreezeWallet(Long walletId) {
        freezeLock.lock();
        try {
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