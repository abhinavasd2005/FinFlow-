package com.finflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.dto.request.FinancialSummaryRequest;
import com.finflow.dto.response.AiTransferDraftResponse;
import com.finflow.dto.response.FinancialSummaryResponse;
import com.finflow.dto.response.TransactionHistoryResponse;
import com.finflow.dto.response.WalletStatementResponse;
import com.finflow.entity.Wallet;
import com.finflow.exception.AiServiceUnavailableException;
import com.finflow.exception.InvalidRequestException;
import com.finflow.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final WalletRepository walletRepository;
    private final TransactionService transactionService;
    private final String apiKey;
    private final String model;

    public AiAssistantService(
            ObjectMapper objectMapper,
            WalletRepository walletRepository,
            TransactionService transactionService,
            @Value("${ai.gemini.api-key:}") String apiKey,
            @Value("${ai.gemini.model:gemini-2.5-flash}") String model,
            @Value("${ai.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.walletRepository = walletRepository;
        this.transactionService = transactionService;
        this.apiKey = apiKey;
        this.model = model;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public boolean isEnabled() {
        return !apiKey.isBlank();
    }

    public AiTransferDraftResponse createTransferDraft(String instruction, String username) {
        requireEnabled();

        List<Wallet> wallets = walletRepository.findByUserUsername(username);
        String walletContext = wallets.stream()
                .map(wallet -> "id=" + wallet.getId()
                        + ", name=" + wallet.getWalletName()
                        + ", walletNumber=" + wallet.getWalletNumber()
                        + ", balance=" + wallet.getBalance())
                .reduce((left, right) -> left + "\\n" + right)
                .orElse("No wallets available");

        String instructions = "You are FinFlow's transfer form assistant. "
                + "Convert the user's natural-language instruction into a transfer draft. "
                + "Never invent a wallet number, wallet ID, amount, or recipient. "
                + "The source wallet must be one of the user's wallets listed below. "
                + "If required information is missing, set needsClarification to true and explain what is missing. "
                + "This is only a draft; never claim that a transfer was executed.\\n\\n"
                + "User's wallets:\\n" + walletContext;

        JsonNode result = callStructured(
                instructions,
                "User instruction: " + instruction,
                transferDraftSchema()
        );

        Long fromWalletId = parseLong(result.path("fromWalletId").asText());
        String destinationWalletNumber = emptyToNull(result.path("destinationWalletNumber").asText());
        BigDecimal amount = parseDecimal(result.path("amount").asText());
        String description = emptyToNull(result.path("description").asText());
        boolean needsClarification = result.path("needsClarification").asBoolean(true);
        String message = emptyToNull(result.path("message").asText());

        boolean ownsSourceWallet = fromWalletId != null && wallets.stream()
                .anyMatch(wallet -> wallet.getId().equals(fromWalletId));
        if (!needsClarification && !ownsSourceWallet) {
            return new AiTransferDraftResponse(
                    null,
                    destinationWalletNumber,
                    amount,
                    description,
                    true,
                    "Choose one of your wallets as the transfer source."
            );
        }

        if (!needsClarification && (destinationWalletNumber == null
                || amount == null
                || amount.signum() <= 0
                || amount.scale() > 2)) {
            return new AiTransferDraftResponse(
                    fromWalletId,
                    destinationWalletNumber,
                    amount,
                    description,
                    true,
                    "Provide a valid destination wallet number and an amount with up to two decimal places."
            );
        }

        return new AiTransferDraftResponse(
                fromWalletId,
                destinationWalletNumber,
                amount,
                description,
                needsClarification,
                message
        );
    }

    public FinancialSummaryResponse createFinancialSummary(
            FinancialSummaryRequest request,
            String username) {
        requireEnabled();

        if (request.getTo().isBefore(request.getFrom())) {
            throw new InvalidRequestException("Summary end must be after summary start");
        }

        WalletStatementResponse statement = transactionService.getWalletStatement(
                request.getWalletId(),
                request.getFrom(),
                request.getTo(),
                username
        );

        String transactions = statement.getTransactions().stream()
                .map(this::compactTransaction)
                .reduce((left, right) -> left + "\\n" + right)
                .orElse("No transactions in this period");

        String instructions = "You are a careful personal-finance summary assistant. "
                + "Use only the supplied wallet data. Do not invent transactions, balances, or causes. "
                + "Write concise, practical observations. Do not give regulated investment advice. "
                + "Return a summary, a short list of highlights, and a short list of recommendations.";

        String input = "Wallet: " + statement.getWalletName()
                + " (" + statement.getWalletNumber() + ")\\n"
                + "Current balance: " + statement.getCurrentBalance() + "\\n"
                + "Total debited: " + statement.getTotalDebited() + "\\n"
                + "Total credited: " + statement.getTotalCredited() + "\\n"
                + "Transactions:\\n" + transactions;

        JsonNode result = callStructured(
                instructions,
                input,
                financialSummarySchema()
        );

        return new FinancialSummaryResponse(
                request.getWalletId(),
                request.getFrom(),
                request.getTo(),
                result.path("summary").asText("No summary available"),
                stringList(result.path("highlights")),
                stringList(result.path("recommendations"))
        );
    }

    private JsonNode callStructured(
            String instructions,
            String input,
            Map<String, Object> schema) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("systemInstruction", Map.of(
                "parts", List.of(Map.of("text", instructions))
        ));
        payload.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", input))
        )));
        payload.put("generationConfig", Map.of(
                "responseMimeType", "application/json",
                "responseSchema", schema
        ));

        try {
            JsonNode response = restClient.post()
                    .uri("/models/" + model + ":generateContent")
                    .header("x-goog-api-key", apiKey)
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class);

            String output = extractOutputText(response);
            if (output == null || output.isBlank()) {
                throw new AiServiceUnavailableException("AI provider returned an empty response");
            }

            return objectMapper.readTree(output);
        } catch (RestClientResponseException ex) {
            String providerMessage = extractProviderMessage(ex.getResponseBodyAsString());
            log.error("Gemini request failed with HTTP {}: {}", ex.getStatusCode().value(), providerMessage);
            throw new AiServiceUnavailableException(
                    "Gemini request failed (HTTP " + ex.getStatusCode().value() + "): " + providerMessage,
                    ex
            );
        } catch (JsonProcessingException | RuntimeException ex) {
            if (ex instanceof AiServiceUnavailableException aiException) {
                throw aiException;
            }
            log.error("Gemini request failed before a response was received", ex);
            throw new AiServiceUnavailableException("AI provider request failed", ex);
        }
    }

    private String extractProviderMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "The provider returned no error details";
        }

        try {
            JsonNode body = objectMapper.readTree(responseBody);
            String message = body.path("error").path("message").asText();
            if (message != null && !message.isBlank()) {
                return message;
            }
        } catch (JsonProcessingException ignored) {
            // Keep a short raw response when the provider does not return JSON.
        }

        return responseBody.length() > 300
                ? responseBody.substring(0, 300)
                : responseBody;
    }

    private String extractOutputText(JsonNode response) {
        if (response == null) {
            return null;
        }
        for (JsonNode candidate : response.path("candidates")) {
            for (JsonNode part : candidate.path("content").path("parts")) {
                if (part.hasNonNull("text")) {
                    return part.path("text").asText();
                }
            }
        }
        return null;
    }

    private Map<String, Object> transferDraftSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("fromWalletId", Map.of("type", "string"));
        properties.put("destinationWalletNumber", Map.of("type", "string"));
        properties.put("amount", Map.of("type", "string"));
        properties.put("description", Map.of("type", "string"));
        properties.put("needsClarification", Map.of("type", "boolean"));
        properties.put("message", Map.of("type", "string"));
        return schema(properties, List.of(
                "fromWalletId", "destinationWalletNumber", "amount",
                "description", "needsClarification", "message"));
    }

    private Map<String, Object> financialSummarySchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("summary", Map.of("type", "string"));
        properties.put("highlights", Map.of(
                "type", "array", "items", Map.of("type", "string")));
        properties.put("recommendations", Map.of(
                "type", "array", "items", Map.of("type", "string")));
        return schema(properties, List.of("summary", "highlights", "recommendations"));
    }

    private Map<String, Object> schema(
            Map<String, Object> properties,
            List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private String compactTransaction(TransactionHistoryResponse transaction) {
        return "type=" + transaction.getType()
                + ", status=" + transaction.getStatus()
                + ", amount=" + transaction.getAmount()
                + ", createdAt=" + transaction.getCreatedAt()
                + ", description=" + transaction.getDescription();
    }

    private List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(value -> values.add(value.asText()));
        }
        return values;
    }

    private Long parseLong(String value) {
        try {
            return value == null || value.isBlank() ? null : Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal parseDecimal(String value) {
        try {
            return value == null || value.isBlank() ? null : new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void requireEnabled() {
        if (!isEnabled()) {
            throw new AiServiceUnavailableException(
                    "AI features are disabled. Set GEMINI_API_KEY to enable them.");
        }
    }
}
