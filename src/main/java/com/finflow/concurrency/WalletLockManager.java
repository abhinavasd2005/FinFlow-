package com.finflow.concurrency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class WalletLockManager {

    private static final Logger log = LoggerFactory.getLogger(WalletLockManager.class);
    private static final long LOCK_TIMEOUT_SECONDS = 5;

    private final ConcurrentHashMap<Long, ReentrantLock> lockMap = new ConcurrentHashMap<>();

    private ReentrantLock getLock(Long walletId) {
        return lockMap.computeIfAbsent(walletId, id -> new ReentrantLock(true));
    }

    public boolean tryLock(Long walletId) {
        try {
            boolean acquired = getLock(walletId).tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (acquired) {
                log.debug("[LOCK] Acquired lock for wallet: {}", walletId);
            } else {
                log.warn("[LOCK] Timeout acquiring lock for wallet: {}", walletId);
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[LOCK] Interrupted while acquiring lock for wallet: {}", walletId);
            return false;
        }
    }

    public boolean tryLockOrdered(Long walletId1, Long walletId2) {
        Long firstId = Math.min(walletId1, walletId2);
        Long secondId = Math.max(walletId1, walletId2);

        if (!tryLock(firstId)) {
            return false;
        }

        if (!tryLock(secondId)) {
            unlock(firstId);
            return false;
        }

        log.debug("[LOCK] Ordered locks acquired: {} -> {}", firstId, secondId);
        return true;
    }

    public void unlock(Long walletId) {
        ReentrantLock lock = lockMap.get(walletId);
        if (lock != null && lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("[LOCK] Released lock for wallet: {}", walletId);
        }
    }

    public void unlockOrdered(Long walletId1, Long walletId2) {
        Long firstId = Math.min(walletId1, walletId2);
        Long secondId = Math.max(walletId1, walletId2);

        unlock(secondId);
        unlock(firstId);

        log.debug("[LOCK] Ordered locks released: {} -> {}", firstId, secondId);
    }

    public boolean isLocked(Long walletId) {
        ReentrantLock lock = lockMap.get(walletId);
        return lock != null && lock.isLocked();
    }

    public int getQueueLength(Long walletId) {
        ReentrantLock lock = lockMap.get(walletId);
        return lock != null ? lock.getQueueLength() : 0;
    }
}