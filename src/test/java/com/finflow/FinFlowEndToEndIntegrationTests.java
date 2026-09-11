package com.finflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.entity.Transaction;
import com.finflow.repository.FraudAlertRepository;
import com.finflow.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "ai.gemini.api-key=",
        "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FinFlowEndToEndIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private FraudAlertRepository fraudAlertRepository;

    @Test
    void completesTheAuthenticatedWalletAndTransferFlow() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        TestIdentity sender = register("sender" + suffix, "sender" + suffix + "@example.test", "+919876543210");
        TestIdentity receiver = register("receiver" + suffix, "receiver" + suffix + "@example.test", "+919876543211");
        TestIdentity stranger = register("stranger" + suffix, "stranger" + suffix + "@example.test", "+919876543212");

        long senderWalletId = createWallet(sender.token(), "Sender wallet", "15000.00", "6000.00");
        long receiverWalletId = createWallet(receiver.token(), "Receiver wallet", "0.00", "100000.00");
        long strangerWalletId = createWallet(stranger.token(), "Stranger wallet", "100.00", "100000.00");

        mockMvc.perform(get("/api/wallets/my-wallets"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/wallets/{walletId}", senderWalletId)
                        .header("Authorization", bearer(receiver.token())))
                .andExpect(status().isNotFound());

        MvcResult lookup = mockMvc.perform(get("/api/wallets/lookup")
                        .header("Authorization", bearer(sender.token()))
                        .param("walletNumber", walletNumber(receiver.token(), receiverWalletId)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(json(lookup).path("id").asLong()).isEqualTo(receiverWalletId);

        String idempotencyKey = "transfer-" + suffix;
        MvcResult firstTransfer = transfer(
                sender.token(),
                senderWalletId,
                receiverWalletId,
                "5000.00",
                idempotencyKey,
                "Rent"
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn();
        long transactionId = json(firstTransfer).path("transactionId").asLong();

        MvcResult replay = transfer(
                sender.token(),
                senderWalletId,
                receiverWalletId,
                "5000.00",
                idempotencyKey,
                "Rent"
        )
                .andExpect(status().isOk())
                .andReturn();
        assertThat(json(replay).path("transactionId").asLong()).isEqualTo(transactionId);

        transfer(
                stranger.token(),
                strangerWalletId,
                receiverWalletId,
                "1.00",
                idempotencyKey,
                "Not allowed"
        )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Idempotency key has already been used"));

        mockMvc.perform(get("/api/transfers/{transactionId}", transactionId)
                        .header("Authorization", bearer(stranger.token())))
                .andExpect(status().isForbidden());

        String from = LocalDateTime.now().minusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String to = LocalDateTime.now().plusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        mockMvc.perform(get("/api/transactions/wallet/{walletId}/filter", senderWalletId)
                        .header("Authorization", bearer(sender.token()))
                        .param("from", from)
                        .param("to", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionId").value(transactionId))
                .andExpect(jsonPath("$[0].type").value("DEBIT"));

        mockMvc.perform(get("/api/transactions/wallet/{walletId}/filter", receiverWalletId)
                        .header("Authorization", bearer(receiver.token()))
                        .param("from", from)
                        .param("to", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("CREDIT"));

        mockMvc.perform(get("/api/transactions/wallet/{walletId}/statement", senderWalletId)
                        .header("Authorization", bearer(sender.token()))
                        .param("from", from)
                        .param("to", to))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDebited").value(5000))
                .andExpect(jsonPath("$.totalCredited").value(0));

        mockMvc.perform(get("/api/transactions/{transactionId}/slip", transactionId)
                        .header("Authorization", bearer(sender.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceAfterTransfer").value(10000))
                .andExpect(jsonPath("$.fromOwnerUsername").value(sender.username()));

        mockMvc.perform(get("/api/transactions/wallet/{walletId}/filter", senderWalletId)
                        .header("Authorization", bearer(sender.token()))
                        .param("from", to)
                        .param("to", from))
                .andExpect(status().isBadRequest());

        waitForFraudAlert(transactionId);
        Transaction scoredTransaction = transactionRepository.findById(transactionId).orElseThrow();
        assertThat(scoredTransaction.getFraudScore()).isGreaterThanOrEqualTo(30);

        mockMvc.perform(post("/api/auth/register/admin")
                        .param("adminSecret", "incorrect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationBody(
                                "admin" + suffix,
                                "admin" + suffix + "@example.test",
                                "+919876543213"))))
                .andExpect(status().isForbidden());

        TestIdentity admin = registerAdmin(
                "admin" + suffix,
                "admin" + suffix + "@example.test",
                "+919876543213"
        );

        mockMvc.perform(get("/api/fraud/alerts")
                        .header("Authorization", bearer(admin.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionId").value(transactionId));

        mockMvc.perform(post("/api/fraud/freeze/{walletId}", senderWalletId)
                        .header("Authorization", bearer(admin.token()))
                        .param("reason", "Manual review"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/wallets/{walletId}", senderWalletId)
                        .header("Authorization", bearer(sender.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));

        transfer(sender.token(), senderWalletId, receiverWalletId, "1.00", "frozen-" + suffix, "Blocked")
                .andExpect(status().isLocked());

        mockMvc.perform(post("/api/fraud/unfreeze/{walletId}", senderWalletId)
                        .header("Authorization", bearer(admin.token())))
                .andExpect(status().isOk());

        transfer(sender.token(), senderWalletId, receiverWalletId, "1.00", "after-unfreeze-" + suffix, "Allowed")
                .andExpect(status().isCreated());

        transfer(sender.token(), senderWalletId, receiverWalletId, "2000.00", "daily-limit-" + suffix, "Over limit")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Daily transfer limit exceeded"));

        transfer(receiver.token(), receiverWalletId, senderWalletId, "6000.00", "insufficient-" + suffix, "Too much")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Insufficient balance"));

        mockMvc.perform(get("/api/ai/status")
                        .header("Authorization", bearer(sender.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(post("/api/ai/transfer-draft")
                        .header("Authorization", bearer(sender.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruction\":\"Send ten dollars\"}"))
                .andExpect(status().isServiceUnavailable());
    }

    private TestIdentity register(String username, String email, String phoneNumber) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationBody(username, email, phoneNumber))))
                .andExpect(status().isCreated())
                .andReturn();
        return identity(result);
    }

    private TestIdentity registerAdmin(String username, String email, String phoneNumber) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register/admin")
                        .param("adminSecret", "test-admin-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registrationBody(username, email, phoneNumber))))
                .andExpect(status().isCreated())
                .andReturn();
        return identity(result);
    }

    private Map<String, Object> registrationBody(String username, String email, String phoneNumber) {
        return Map.of(
                "username", username,
                "email", email,
                "password", "password123",
                "phoneNumber", phoneNumber
        );
    }

    private long createWallet(String token, String walletName, String initialBalance, String dailyLimit) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/wallets")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "walletName", walletName,
                                "initialBalance", new BigDecimal(initialBalance),
                                "dailyLimit", new BigDecimal(dailyLimit)
                        ))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).path("id").asLong();
    }

    private String walletNumber(String token, long walletId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/wallets/{walletId}", walletId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).path("walletNumber").asText();
    }

    private ResultActions transfer(
            String token,
            long fromWalletId,
            long toWalletId,
            String amount,
            String idempotencyKey,
            String description
    ) throws Exception {
        return mockMvc.perform(post("/api/transfers")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fromWalletId", fromWalletId,
                        "toWalletId", toWalletId,
                        "amount", new BigDecimal(amount),
                        "idempotencyKey", idempotencyKey,
                        "description", description
                ))));
    }

    private TestIdentity identity(MvcResult result) throws Exception {
        JsonNode body = json(result);
        return new TestIdentity(
                body.path("token").asText(),
                body.path("user").path("username").asText()
        );
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void waitForFraudAlert(long transactionId) throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            if (!fraudAlertRepository.findByTransactionId(transactionId).isEmpty()) {
                return;
            }
            Thread.sleep(100);
        }
        fail("Timed out waiting for fraud alert for transaction " + transactionId);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record TestIdentity(String token, String username) {
    }
}
