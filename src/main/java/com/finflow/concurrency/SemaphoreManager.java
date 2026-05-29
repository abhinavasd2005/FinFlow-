package com.finflow.concurrency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
public class SemaphoreManager {

    private static final Logger log = LoggerFactory.getLogger(SemaphoreManager.class);
    private static final int MAX_CONCURRENT_PER_WALLET = 3;
    private static final long ACQUIRE_TIMEOUT_SECONDS = 5;

    private final ConcurrentHashMap<Long, Semaphore> semaphoreMap = new ConcurrentHashMap<>();

    private Semaphore getSemaphore(Long walletId) {
        return semaphoreMap.computeIfAbsent(
                walletId,
                id -> new Semaphore(MAX_CONCURRENT_PER_WALLET, true)
        );
    }

    public boolean acquire(Long walletId) {
        try {
            boolean acquired = getSemaphore(walletId)
                    .tryAcquire(ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (acquired) {
                log.debug("[SEMAPHORE] Acquired permit for wallet: {} | Remaining: {}",
                        walletId, getAvailablePermits(walletId));
            } else {
                log.warn("[SEMAPHORE] Timeout — wallet {} is overloaded", walletId);
            }

            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[SEMAPHORE] Interrupted for wallet: {}", walletId);
            return false;
        }
    }

    public boolean acquireOrdered(Long walletId1, Long walletId2) {
        Long firstId = Math.min(walletId1, walletId2);
        Long secondId = Math.max(walletId1, walletId2);

        if (!acquire(firstId)) {
            return false;
        }

        if (!acquire(secondId)) {
            release(firstId);
            return false;
        }

        log.debug("[SEMAPHORE] Ordered permits acquired: {} -> {}", firstId, secondId);
        return true;
    }

    public void release(Long walletId) {
        Semaphore semaphore = semaphoreMap.get(walletId);
        if (semaphore != null) {
            semaphore.release();
            log.debug("[SEMAPHORE] Released permit for wallet: {} | Available: {}",
                    walletId, semaphore.availablePermits());
        }
    }

    public void releaseOrdered(Long walletId1, Long walletId2) {
        Long firstId = Math.min(walletId1, walletId2);
        Long secondId = Math.max(walletId1, walletId2);

        release(secondId);
        release(firstId);

        log.debug("[SEMAPHORE] Ordered permits released: {} -> {}", firstId, secondId);
    }

    public int getAvailablePermits(Long walletId) {
        Semaphore semaphore = semaphoreMap.get(walletId);
        return semaphore != null ? semaphore.availablePermits() : MAX_CONCURRENT_PER_WALLET;
    }

    public int getQueuedThreads(Long walletId) {
        Semaphore semaphore = semaphoreMap.get(walletId);
        return semaphore != null ? semaphore.getQueueLength() : 0;
    }
}