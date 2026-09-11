package com.finflow.controller;

import com.finflow.dto.request.AiTransferDraftRequest;
import com.finflow.dto.request.FinancialSummaryRequest;
import com.finflow.dto.response.AiTransferDraftResponse;
import com.finflow.dto.response.FinancialSummaryResponse;
import com.finflow.service.AiAssistantService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiAssistantService aiAssistantService;

    public AiController(AiAssistantService aiAssistantService) {
        this.aiAssistantService = aiAssistantService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "enabled", aiAssistantService.isEnabled(),
                "features", Map.of(
                        "transferDraft", aiAssistantService.isEnabled(),
                        "financialSummary", aiAssistantService.isEnabled()
                )
        ));
    }

    @PostMapping("/transfer-draft")
    public ResponseEntity<AiTransferDraftResponse> createTransferDraft(
            @Valid @RequestBody AiTransferDraftRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(aiAssistantService.createTransferDraft(
                request.getInstruction(),
                userDetails.getUsername()
        ));
    }

    @PostMapping("/financial-summary")
    public ResponseEntity<FinancialSummaryResponse> createFinancialSummary(
            @Valid @RequestBody FinancialSummaryRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(aiAssistantService.createFinancialSummary(
                request,
                userDetails.getUsername()
        ));
    }
}
