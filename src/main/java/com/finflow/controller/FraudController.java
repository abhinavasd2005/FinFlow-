package com.finflow.controller;

import com.finflow.concurrency.AccountFreezeManager;
import com.finflow.entity.FraudAlert;
import com.finflow.exception.FraudAlertNotFoundException;
import com.finflow.fraud.FraudQueueProcessor;
import com.finflow.repository.FraudAlertRepository;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import com.finflow.dto.response.FraudAlertResponse;
import com.finflow.service.FraudDetectionService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fraud")
@Validated
public class FraudController {

    private final FraudAlertRepository fraudAlertRepository;
    private final AccountFreezeManager accountFreezeManager;
    private final FraudQueueProcessor fraudQueueProcessor;
    private final FraudDetectionService fraudDetectionService;

    public FraudController(FraudAlertRepository fraudAlertRepository,
                           AccountFreezeManager accountFreezeManager,
                           FraudQueueProcessor fraudQueueProcessor,
                           FraudDetectionService fraudDetectionService){
        this.fraudAlertRepository = fraudAlertRepository;
        this.accountFreezeManager = accountFreezeManager;
        this.fraudQueueProcessor = fraudQueueProcessor;
        this.fraudDetectionService = fraudDetectionService;
    }
    @GetMapping("/alerts")
    public ResponseEntity<List<FraudAlertResponse>> getAlerts() {

        return ResponseEntity.ok(fraudDetectionService.getAllAlerts());
    }

    @GetMapping("/alerts/wallet/{walletId}")
    public ResponseEntity<List<FraudAlertResponse>> getAlertsByWallet(@PathVariable Long walletId) {
        return ResponseEntity.ok(fraudDetectionService.getAlertsByWallet(walletId));
    }

    @PostMapping("/freeze/{walletId}")
    public ResponseEntity<Map<String, String>> freezeWallet(
            @PathVariable Long walletId,
            @RequestParam @NotBlank @Size(max = 500) String reason) {
        accountFreezeManager.freezeWallet(walletId, reason);
        return ResponseEntity.ok(Map.of(
                "message", "Wallet " + walletId + " frozen",
                "reason", reason
        ));
    }

    @PostMapping("/unfreeze/{walletId}")
    public ResponseEntity<Map<String, String>> unfreezeWallet(@PathVariable Long walletId) {
        accountFreezeManager.unfreezeWallet(walletId);
        return ResponseEntity.ok(Map.of(
                "message", "Wallet " + walletId + " unfrozen"
        ));
    }

    @GetMapping("/queue/size")
    public ResponseEntity<Map<String, Integer>> getQueueSize() {
        return ResponseEntity.ok(Map.of(
                "pendingInQueue", fraudQueueProcessor.getQueueSize()
        ));
    }

    @PatchMapping("/alerts/{alertId}/dismiss")
    public ResponseEntity<Map<String, String>> dismissAlert(@PathVariable Long alertId) {
        FraudAlert alert = fraudAlertRepository.findById(alertId)
                .orElseThrow(() -> new FraudAlertNotFoundException("Alert not found: " + alertId));
        alert.setStatus(FraudAlert.AlertStatus.DISMISSED);
        fraudAlertRepository.save(alert);
        return ResponseEntity.ok(Map.of("message", "Alert dismissed"));
    }

    @PatchMapping("/alerts/{alertId}/review")
    public ResponseEntity<Map<String, String>> markReviewed(@PathVariable Long alertId) {
        FraudAlert alert = fraudAlertRepository.findById(alertId)
                .orElseThrow(() -> new FraudAlertNotFoundException("Alert not found: " + alertId));
        alert.setStatus(FraudAlert.AlertStatus.REVIEWED);
        fraudAlertRepository.save(alert);
        return ResponseEntity.ok(Map.of("message", "Alert marked reviewed"));
    }
}
