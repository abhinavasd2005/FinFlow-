package com.finflow.service;

import com.finflow.dto.request.CreateWalletRequest;
import com.finflow.dto.response.WalletResponse;
import com.finflow.entity.User;
import com.finflow.entity.Wallet;
import com.finflow.enums.WalletStatus;
import com.finflow.exception.ForbiddenOperationException;
import com.finflow.exception.InvalidRequestException;
import com.finflow.exception.ResourceNotFoundException;
import com.finflow.mapper.EntityMapper;
import com.finflow.repository.UserRepository;
import com.finflow.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class WalletService {

    private static final BigDecimal DEFAULT_DAILY_LIMIT = new BigDecimal("100000.00");

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;

    public WalletService(WalletRepository walletRepository,
                         UserRepository userRepository) {
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        BigDecimal initialBalance = request.getInitialBalance() == null
                ? BigDecimal.ZERO
                : request.getInitialBalance();

        BigDecimal dailyLimit = request.getDailyLimit() == null
                ? DEFAULT_DAILY_LIMIT
                : request.getDailyLimit();

        if (initialBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidRequestException("Initial balance cannot be negative");
        }

        if (dailyLimit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Daily limit must be greater than zero");
        }

        Wallet wallet = new Wallet();
        wallet.setUser(user);
        wallet.setWalletNumber(generateUniqueWalletNumber());
        wallet.setWalletName(request.getWalletName());
        wallet.setBalance(normalize(initialBalance));
        wallet.setDailyLimit(normalize(dailyLimit));
        wallet.setStatus(WalletStatus.ACTIVE);
        wallet.setCreatedAt(LocalDateTime.now());
        wallet.setUpdatedAt(LocalDateTime.now());

        Wallet savedWallet = walletRepository.save(wallet);
        return EntityMapper.toWalletResponse(savedWallet);
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(Long walletId, String username) {
        Wallet wallet = walletRepository.findByIdAndUserUsername(walletId, username)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
        return EntityMapper.toWalletResponse(wallet);
    }

    @Transactional(readOnly = true)
    public List<WalletResponse> getUserWallets(String username) {
        if (!userRepository.existsByUsername(username)) {
            throw new ResourceNotFoundException("User not found");
        }

        return walletRepository.findByUserUsername(username)
                .stream()
                .map(EntityMapper::toWalletResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(Long walletId, String username) {
        Wallet wallet = walletRepository.findByIdAndUserUsername(walletId, username)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
        return wallet.getBalance();
    }

    @Transactional
    public WalletResponse setDailyLimit(Long walletId, BigDecimal limit, String username) {
        if (limit == null || limit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidRequestException("Daily limit must be greater than zero");
        }

        Wallet wallet = walletRepository.findByIdAndUserUsername(walletId, username)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

        wallet.setDailyLimit(normalize(limit));
        wallet.setUpdatedAt(LocalDateTime.now());

        Wallet saved = walletRepository.save(wallet);
        return EntityMapper.toWalletResponse(saved);
    }

    private String generateUniqueWalletNumber() {
        String walletNumber;
        do {
            walletNumber = "WLT-" + UUID.randomUUID().toString()
                    .replace("-", "")
                    .substring(0, 10)
                    .toUpperCase();
        } while (walletRepository.existsByWalletNumber(walletNumber));
        return walletNumber;
    }

    private BigDecimal normalize(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}