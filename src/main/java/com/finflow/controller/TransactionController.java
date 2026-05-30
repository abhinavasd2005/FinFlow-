package com.finflow.controller;

import com.finflow.dto.response.TransactionHistoryResponse;
import com.finflow.dto.response.TransferSlipResponse;
import com.finflow.dto.response.WalletStatementResponse;
import com.finflow.service.TransactionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping("/wallet/{walletId}")
    public ResponseEntity<List<TransactionHistoryResponse>> getWalletHistory(
            @PathVariable Long walletId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                transactionService.getWalletHistory(walletId, userDetails.getUsername()));
    }

    @GetMapping("/wallet/{walletId}/filter")
    public ResponseEntity<List<TransactionHistoryResponse>> getByDateRange(
            @PathVariable Long walletId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                transactionService.getWalletHistoryByDateRange(walletId, from, to, userDetails.getUsername()));
    }

    @GetMapping("/wallet/{walletId}/statement")
    public ResponseEntity<WalletStatementResponse> getStatement(
            @PathVariable Long walletId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                transactionService.getWalletStatement(walletId, from, to, userDetails.getUsername()));
    }

    @GetMapping("/{transactionId}/slip")
    public ResponseEntity<TransferSlipResponse> getSlip(
            @PathVariable Long transactionId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                transactionService.getTransferSlip(transactionId, userDetails.getUsername()));
    }
}