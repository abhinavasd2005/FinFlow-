package com.finflow.mapper;

import com.finflow.dto.response.TransferResponse;
import com.finflow.dto.response.UserResponse;
import com.finflow.dto.response.WalletResponse;
import com.finflow.entity.Transaction;
import com.finflow.entity.User;
import com.finflow.entity.Wallet;
import com.finflow.dto.response.FraudAlertResponse;
import com.finflow.entity.FraudAlert;

public final class EntityMapper {

    private EntityMapper() {
    }

    public static UserResponse toUserResponse(User user) {
        if (user == null) {
            return null;
        }

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getCreatedAt(),
                user.getRole().name()
        );
    }

    public static WalletResponse toWalletResponse(Wallet wallet) {
        if (wallet == null) {
            return null;
        }

        return new WalletResponse(
                wallet.getId(),
                wallet.getWalletNumber(),
                wallet.getWalletName(),
                wallet.getBalance(),
                wallet.getDailyLimit(),
                wallet.getStatus(),
                wallet.getCreatedAt()
        );
    }

    public static TransferResponse toTransferResponse(Transaction transaction) {
        if (transaction == null) {
            return null;
        }

        return new TransferResponse(
                transaction.getId(),
                transaction.getReferenceNumber(),
                transaction.getTransactionType(),
                transaction.getStatus(),
                transaction.getAmount(),
                transaction.getFromWallet() != null ? transaction.getFromWallet().getId() : null,
                transaction.getToWallet() != null ? transaction.getToWallet().getId() : null,
                transaction.getDescription(),
                transaction.getFailureReason(),
                transaction.getFraudScore(),
                transaction.getCreatedAt()
        );
    }
    public static FraudAlertResponse toFraudAlertResponse(FraudAlert fraudAlert) {

        FraudAlertResponse response = new FraudAlertResponse();

        response.setId(fraudAlert.getId());

        response.setTransactionId(
                fraudAlert.getTransaction().getId()
        );

        response.setTransactionReference(
                fraudAlert.getTransaction().getReferenceNumber()
        );

        response.setFraudScore(
                fraudAlert.getFraudScore()
        );

        response.setAlertStatus(
                fraudAlert.getStatus().name()
        );

        response.setTriggeredRules(
                fraudAlert.getTriggeredRules()
        );

        response.setNotes(
                fraudAlert.getNotes()
        );

        response.setCreatedAt(
                fraudAlert.getCreatedAt()
        );

        return response;
    }
}