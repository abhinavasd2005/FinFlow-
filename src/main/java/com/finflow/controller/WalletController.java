package com.finflow.controller;

import com.finflow.dto.request.CreateWalletRequest;
import com.finflow.dto.response.WalletResponse;
import com.finflow.service.WalletService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/wallets")
@Validated
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(
            @Valid @RequestBody CreateWalletRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(walletService.createWallet(request, userDetails.getUsername()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WalletResponse> getWallet(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(walletService.getWallet(id, userDetails.getUsername()));
    }

    @GetMapping("/my-wallets")
    public ResponseEntity<List<WalletResponse>> getMyWallets(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(walletService.getUserWallets(userDetails.getUsername()));
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<BigDecimal> getBalance(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(walletService.getBalance(id, userDetails.getUsername()));
    }

    @PutMapping("/{id}/limit")
    public ResponseEntity<WalletResponse> setDailyLimit(
            @PathVariable Long id,
            @RequestParam @Positive BigDecimal limit,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(walletService.setDailyLimit(id, limit, userDetails.getUsername()));
    }
}