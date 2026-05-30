package com.finflow.service;

import com.finflow.async.TransferMetrics;
import com.finflow.concurrency.AccountFreezeManager;
import com.finflow.concurrency.SemaphoreManager;
import com.finflow.concurrency.WalletLockManager;
import com.finflow.dto.request.TransferRequest;
import com.finflow.dto.response.TransferResponse;
import com.finflow.entity.Transaction;
import com.finflow.entity.User;
import com.finflow.entity.Wallet;
import com.finflow.enums.TransactionStatus;
import com.finflow.enums.TransactionType;
import com.finflow.enums.WalletStatus;
import com.finflow.exception.*;
import com.finflow.mapper.EntityMapper;
import com.finflow.repository.TransactionRepository;
import com.finflow.repository.UserRepository;
import com.finflow.repository.WalletRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class TransferService {

    private static final BigDecimal PESSIMISTIC_LOCK_THRESHOLD = new BigDecimal("10000.00");

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerService ledgerService;
    private final UserRepository userRepository;
    private final FraudDetectionService fraudDetectionService;
    private final NotificationService notificationService;
    private final WalletLockManager walletLockManager;
    private final SemaphoreManager semaphoreManager;
    private final AccountFreezeManager accountFreezeManager;
    private final TransferMetrics transferMetrics;

    public TransferService(
            WalletRepository walletRepository,
            TransactionRepository transactionRepository,
            LedgerService ledgerService,
            UserRepository userRepository,
            FraudDetectionService fraudDetectionService,
            NotificationService notificationService,
            WalletLockManager walletLockManager,
            SemaphoreManager semaphoreManager,
            AccountFreezeManager accountFreezeManager,
            TransferMetrics transferMetrics
    ) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerService = ledgerService;
        this.userRepository = userRepository;
        this.fraudDetectionService = fraudDetectionService;
        this.notificationService = notificationService;
        this.walletLockManager = walletLockManager;
        this.semaphoreManager = semaphoreManager;
        this.accountFreezeManager = accountFreezeManager;
        this.transferMetrics = transferMetrics;
    }

    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional(
            isolation = Isolation.READ_COMMITTED,
            noRollbackFor = {
                    InsufficientBalanceException.class,
                    DailyLimitExceededException.class,
                    InvalidRequestException.class,
                    ForbiddenOperationException.class
            }
    )
    public TransferResponse processTransfer(TransferRequest request, String username) {
        validateRequest(request);

        transactionRepository.findByIdempotencyKey(request.getIdempotencyKey())
                .ifPresent(existing -> {
                    throw new IdempotencyKeyExistsException(EntityMapper.toTransferResponse(existing));
                });

        if (accountFreezeManager.isFrozen(request.getFromWalletId())) {
            throw new FrozenWalletException("Wallet is frozen: "
                    + accountFreezeManager.getFreezeReason(request.getFromWalletId()));
        }

        if (accountFreezeManager.isFrozen(request.getToWalletId())) {
            throw new FrozenWalletException("Destination wallet is frozen: "
                    + accountFreezeManager.getFreezeReason(request.getToWalletId()));
        }

        boolean semaphoresAcquired = false;
        boolean locksAcquired = false;

        try {
            semaphoresAcquired = semaphoreManager.acquireOrdered(
                    request.getFromWalletId(),
                    request.getToWalletId()
            );

            if (!semaphoresAcquired) {
                throw new WalletBusyException("Wallet is busy, please try again");
            }

            locksAcquired = walletLockManager.tryLockOrdered(
                    request.getFromWalletId(),
                    request.getToWalletId()
            );

            if (!locksAcquired) {
                throw new WalletBusyException("Could not acquire wallet lock, please try again");
            }

            User sender = userRepository.findByUsername(username)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            Long firstId = Math.min(request.getFromWalletId(), request.getToWalletId());
            Long secondId = Math.max(request.getFromWalletId(), request.getToWalletId());

            boolean highValue = request.getAmount().compareTo(PESSIMISTIC_LOCK_THRESHOLD) > 0;

            Wallet firstWallet = loadWallet(firstId, highValue);
            Wallet secondWallet = loadWallet(secondId, highValue);

            Wallet fromWallet = firstId.equals(request.getFromWalletId()) ? firstWallet : secondWallet;
            Wallet toWallet = firstId.equals(request.getToWalletId()) ? firstWallet : secondWallet;

            if (!fromWallet.getUser().getId().equals(sender.getId())) {
                throw new ForbiddenOperationException("You do not own the source wallet");
            }

            if (fromWallet.getStatus() != WalletStatus.ACTIVE || toWallet.getStatus() != WalletStatus.ACTIVE) {
                throw new InvalidRequestException("One of the wallets is not active");
            }

            LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
            BigDecimal spentToday = transactionRepository.sumTransactionsAfter(fromWallet.getId(), startOfDay);

            if (spentToday.add(request.getAmount()).compareTo(fromWallet.getDailyLimit()) > 0) {
                Transaction failed = buildTransaction(
                        request,
                        fromWallet,
                        toWallet,
                        TransactionStatus.FAILED,
                        "Daily transfer limit exceeded"
                );
                transactionRepository.saveAndFlush(failed);
                transferMetrics.recordFailure();
                throw new DailyLimitExceededException("Daily transfer limit exceeded");
            }

            if (fromWallet.getBalance().compareTo(request.getAmount()) < 0) {
                Transaction failed = buildTransaction(
                        request,
                        fromWallet,
                        toWallet,
                        TransactionStatus.FAILED,
                        "Insufficient balance"
                );
                transactionRepository.saveAndFlush(failed);
                transferMetrics.recordFailure();
                throw new InsufficientBalanceException("Insufficient balance");
            }

            Transaction transaction = buildTransaction(
                    request,
                    fromWallet,
                    toWallet,
                    TransactionStatus.PENDING,
                    null
            );
            transaction.setTransactionType(TransactionType.TRANSFER);

            transactionRepository.saveAndFlush(transaction);

            BigDecimal newFromBalance = fromWallet.getBalance().subtract(request.getAmount());
            BigDecimal newToBalance = toWallet.getBalance().add(request.getAmount());

            fromWallet.setBalance(newFromBalance);
            walletRepository.saveAndFlush(fromWallet);

            toWallet.setBalance(newToBalance);
            walletRepository.saveAndFlush(toWallet);

            ledgerService.createDebitEntry(transaction, fromWallet, request.getAmount(), newFromBalance);
            ledgerService.createCreditEntry(transaction, toWallet, request.getAmount(), newToBalance);

            transaction.setStatus(TransactionStatus.COMPLETED);
            Transaction savedTransaction = transactionRepository.saveAndFlush(transaction);

            BigDecimal transferAmount = request.getAmount();
            Transaction finalTransaction = savedTransaction;

            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    transferMetrics.recordSuccess(transferAmount);
                    fraudDetectionService.analyze(finalTransaction);
                    notificationService.send(finalTransaction);
                }
            });

            return EntityMapper.toTransferResponse(savedTransaction);
        } finally {
            if (locksAcquired) {
                walletLockManager.unlockOrdered(
                        request.getFromWalletId(),
                        request.getToWalletId()
                );
            }

            if (semaphoresAcquired) {
                semaphoreManager.releaseOrdered(
                        request.getFromWalletId(),
                        request.getToWalletId()
                );
            }
        }
    }

    @Transactional(readOnly = true)
    public TransferResponse getTransaction(Long transactionId, String username) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found"));

        String fromOwner = transaction.getFromWallet().getUser().getUsername();
        String toOwner = transaction.getToWallet().getUser().getUsername();

        if (!username.equals(fromOwner) && !username.equals(toOwner)) {
            throw new ForbiddenOperationException("You do not have access to this transaction");
        }

        return EntityMapper.toTransferResponse(transaction);
    }

    private void validateRequest(TransferRequest request) {
        if (request.getFromWalletId() == null || request.getToWalletId() == null) {
            throw new InvalidRequestException("Wallet IDs are required");
        }

        if (request.getFromWalletId().equals(request.getToWalletId())) {
            throw new InvalidRequestException("Cannot transfer to the same wallet");
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Amount must be greater than zero");
        }

        if (request.getAmount().scale() > 2) {
            throw new InvalidRequestException("Amount cannot have more than 2 decimal places");
        }

        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            throw new InvalidRequestException("Idempotency key is required");
        }
    }

    private Wallet loadWallet(Long walletId, boolean highValue) {
        return (highValue
                ? walletRepository.findByIdWithPessimisticLock(walletId)
                : walletRepository.findByIdWithOptimisticLock(walletId))
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found: " + walletId));
    }

    private Transaction buildTransaction(
            TransferRequest request,
            Wallet fromWallet,
            Wallet toWallet,
            TransactionStatus status,
            String failureReason
    ) {
        Transaction transaction = new Transaction();
        transaction.setFromWallet(fromWallet);
        transaction.setToWallet(toWallet);
        transaction.setTransactionType(TransactionType.TRANSFER);
        transaction.setAmount(request.getAmount());
        transaction.setStatus(status);
        transaction.setIdempotencyKey(request.getIdempotencyKey());
        transaction.setDescription(request.getDescription());
        transaction.setFailureReason(failureReason);
        transaction.setFraudScore(0);
        transaction.setReferenceNumber(generateReferenceNumber());
        return transaction;
    }

    private String generateReferenceNumber() {
        return "TXN-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();
    }
}