package com.finflow.repository;

import com.finflow.entity.Transaction;
import com.finflow.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    List<Transaction> findByFromWallet_IdOrToWallet_IdOrderByCreatedAtDesc(Long fromWalletId, Long toWalletId);

    @Query("""
            SELECT t
            FROM Transaction t
            WHERE (t.fromWallet.id = :walletId OR t.toWallet.id = :walletId)
              AND t.createdAt BETWEEN :startTime AND :endTime
            ORDER BY t.createdAt DESC
            """)
    List<Transaction> findByWalletAndDateRange(
            @Param("walletId") Long walletId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    @Query("""
            SELECT COUNT(t)
            FROM Transaction t
            WHERE t.fromWallet.id = :walletId
              AND t.createdAt >= :since
              AND t.status = com.finflow.enums.TransactionStatus.COMPLETED
            """)
    long countRecentTransactions(
            @Param("walletId") Long walletId,
            @Param("since") LocalDateTime since
    );

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            WHERE t.fromWallet.id = :walletId
              AND t.createdAt >= :since
              AND t.status = com.finflow.enums.TransactionStatus.COMPLETED
            """)
    BigDecimal sumTransactionsAfter(
            @Param("walletId") Long walletId,
            @Param("since") LocalDateTime since
    );

    @Query("""
            SELECT COALESCE(AVG(t.amount), 0)
            FROM Transaction t
            WHERE t.fromWallet.id = :walletId
              AND t.status = com.finflow.enums.TransactionStatus.COMPLETED
            """)
    BigDecimal avgTransactionAmount(@Param("walletId") Long walletId);
}