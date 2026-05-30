package com.finflow.service;

import com.finflow.dto.response.TransactionHistoryResponse;
import com.finflow.dto.response.TransferSlipResponse;
import com.finflow.dto.response.WalletStatementResponse;
import com.finflow.entity.LedgerEntry;
import com.finflow.entity.Transaction;
import com.finflow.entity.Wallet;
import com.finflow.repository.LedgerEntryRepository;
import com.finflow.repository.TransactionRepository;
import com.finflow.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              WalletRepository walletRepository,
                              LedgerEntryRepository ledgerEntryRepository) {
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional(readOnly = true)
    public List<TransactionHistoryResponse> getWalletHistory(Long walletId, String username) {
        Wallet wallet = getOwnedWallet(walletId, username);

        List<Transaction> transactions =
                transactionRepository.findByFromWallet_IdOrToWallet_IdOrderByCreatedAtDesc(walletId, walletId);

        return transactions.stream()
                .map(tx -> toHistoryResponse(tx, walletId))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TransactionHistoryResponse> getWalletHistoryByDateRange(Long walletId,
                                                                        LocalDateTime from,
                                                                        LocalDateTime to,
                                                                        String username) {
        getOwnedWallet(walletId, username);

        return transactionRepository.findByWalletAndDateRange(walletId, from, to)
                .stream()
                .map(tx -> toHistoryResponse(tx, walletId))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WalletStatementResponse getWalletStatement(Long walletId,
                                                      LocalDateTime from,
                                                      LocalDateTime to,
                                                      String username) {
        Wallet wallet = getOwnedWallet(walletId, username);

        List<TransactionHistoryResponse> transactions =
                getWalletHistoryByDateRange(walletId, from, to, username);

        BigDecimal totalDebited = transactions.stream()
                .filter(t -> "DEBIT".equals(t.getType()) && "COMPLETED".equals(t.getStatus()))
                .map(TransactionHistoryResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredited = transactions.stream()
                .filter(t -> "CREDIT".equals(t.getType()) && "COMPLETED".equals(t.getStatus()))
                .map(TransactionHistoryResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new WalletStatementResponse(
                wallet.getId(),
                wallet.getWalletNumber(),
                wallet.getWalletName(),
                wallet.getBalance(),
                totalDebited,
                totalCredited,
                transactions.size(),
                from,
                to,
                transactions
        );
    }

    @Transactional(readOnly = true)
    public TransferSlipResponse getTransferSlip(Long transactionId, String username) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        boolean isOwner =
                tx.getFromWallet().getUser().getUsername().equals(username) ||
                        tx.getToWallet().getUser().getUsername().equals(username);

        if (!isOwner) {
            throw new RuntimeException("Access denied");
        }

        BigDecimal balanceAfterTransfer = BigDecimal.ZERO;

        List<LedgerEntry> entries = ledgerEntryRepository.findByTransaction_IdOrderByCreatedAtAsc(transactionId);
        for (LedgerEntry entry : entries) {
            if (entry.getWallet().getUser().getUsername().equals(username)) {
                balanceAfterTransfer = entry.getBalanceAfter();
                break;
            }
        }

        return new TransferSlipResponse(
                tx.getId(),
                tx.getReferenceNumber(),
                tx.getStatus().name(),
                tx.getAmount(),
                tx.getFromWallet().getWalletNumber(),
                tx.getFromWallet().getWalletName(),
                tx.getFromWallet().getUser().getUsername(),
                tx.getToWallet().getWalletNumber(),
                tx.getToWallet().getWalletName(),
                tx.getToWallet().getUser().getUsername(),
                balanceAfterTransfer,
                tx.getDescription(),
                tx.getFailureReason(),
                tx.getFraudScore(),
                tx.getCreatedAt()
        );
    }

    private Wallet getOwnedWallet(Long walletId, String username) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        if (!wallet.getUser().getUsername().equals(username)) {
            throw new RuntimeException("Access denied");
        }

        return wallet;
    }

    private TransactionHistoryResponse toHistoryResponse(Transaction tx, Long requestingWalletId) {
        String type = tx.getFromWallet() != null && tx.getFromWallet().getId().equals(requestingWalletId)
                ? "DEBIT"
                : "CREDIT";

        return new TransactionHistoryResponse(
                tx.getId(),
                tx.getReferenceNumber(),
                type,
                tx.getStatus().name(),
                tx.getAmount(),
                tx.getFromWallet() != null ? tx.getFromWallet().getId() : null,
                tx.getFromWallet() != null ? tx.getFromWallet().getWalletNumber() : null,
                tx.getToWallet() != null ? tx.getToWallet().getId() : null,
                tx.getToWallet() != null ? tx.getToWallet().getWalletNumber() : null,
                tx.getDescription(),
                tx.getFailureReason(),
                tx.getFraudScore(),
                tx.getCreatedAt()
        );
    }
}