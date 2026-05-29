package com.finflow.controller;

import com.finflow.async.TransferMetrics;
import com.finflow.dto.request.TransferRequest;
import com.finflow.dto.response.TransferResponse;
import com.finflow.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;
    private final TransferMetrics transferMetrics;

    public TransferController(TransferService transferService, TransferMetrics transferMetrics) {
        this.transferService = transferService;
        this.transferMetrics = transferMetrics;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> transfer(
            @Valid @RequestBody TransferRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        TransferResponse response = transferService.processTransfer(request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransferResponse> getTransaction(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(transferService.getTransaction(id, userDetails.getUsername()));
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        return ResponseEntity.ok(Map.of(
                "totalTransfers", transferMetrics.getTotalTransfers(),
                "successfulTransfers", transferMetrics.getSuccessfulTransfers(),
                "failedTransfers", transferMetrics.getFailedTransfers(),
                "fraudFlagged", transferMetrics.getFraudFlagged(),
                "totalAmountTransferred", transferMetrics.getTotalAmountTransferred()
        ));
    }
}