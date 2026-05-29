package com.finflow.repository;

import com.finflow.entity.FraudAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, Long> {

    List<FraudAlert> findByStatus(FraudAlert.AlertStatus status);

    List<FraudAlert> findByTransactionId(Long transactionId);

    @Query("SELECT f FROM FraudAlert f WHERE " +
            "f.transaction.fromWallet.id = :walletId " +
            "ORDER BY f.createdAt DESC")
    List<FraudAlert> findByWalletId(@Param("walletId") Long walletId);

    @Query("SELECT COUNT(f) FROM FraudAlert f WHERE " +
            "f.transaction.fromWallet.id = :walletId AND " +
            "f.status = 'PENDING'")
    long countPendingByWalletId(@Param("walletId") Long walletId);
}