package com.narturebelle.payments.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.narturebelle.payments.TestDatabase;
import com.narturebelle.payments.care.CallerProfile;
import com.narturebelle.payments.care.CareDirectory;
import com.narturebelle.payments.model.Role;
import com.narturebelle.payments.outbox.OutboxEvent;
import com.narturebelle.payments.outbox.OutboxEventRepository;
import com.narturebelle.payments.repository.OrderRepository;
import com.narturebelle.payments.repository.PaymentRepository;
import com.narturebelle.payments.repository.PaymentWebhookEventRepository;
import com.narturebelle.payments.repository.PlanValidityRepository;
import com.narturebelle.payments.security.FirebaseTokenVerifier;
import com.narturebelle.payments.security.VerifiedFirebaseToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Copied from Care: the mock checkout exercises the webhook path. Care's session grants are now one saved event. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentWebhookTest {

    private static final String TOKEN = "payment-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentWebhookEventRepository paymentWebhookEventRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PlanValidityRepository planValidityRepository;

    @Value("${payments.mock.webhook-secret}")
    private String webhookSecret;

    @MockBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @MockBean
    private CareDirectory careDirectory;

    @BeforeEach
    void setUp() throws Exception {
        TestDatabase.clearOrders(jdbcTemplate);

        when(careDirectory.findCaller("uid-payment")).thenReturn(Optional.of(new CallerProfile(7L, Set.of(Role.MOTHER))));
        when(firebaseTokenVerifier.verify(TOKEN))
                .thenReturn(new VerifiedFirebaseToken("uid-payment", "payment@example.com", true, null, null));
    }

    private Long createOrderAndPayment(String idempotencyKey) throws Exception {
        String orderBody = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":1,\"paymentMode\":\"ONE_TIME\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long orderId = Long.valueOf(orderBody.replaceAll(".*\"id\":(\\d+).*", "$1"));

        String paymentBody = mockMvc.perform(post("/api/v1/payments/create")
                        .header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return Long.valueOf(paymentBody.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void mockSucceed_marksOrderPaidAndSavesTheEvent() throws Exception {
        Long paymentId = createOrderAndPayment("webhook-key-1");

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/mock/succeed")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYMENT_SUCCEEDED"));

        assertEquals("SUCCEEDED", paymentRepository.findById(paymentId).orElseThrow().getStatus().name());
        assertEquals("PAID", orderRepository.findAll().get(0).getStatus().name());

        OutboxEvent event = outboxEventRepository.findAll().get(0);
        assertEquals("PaymentSucceeded", event.getEventType());
        assertEquals("payment-succeeded:" + orderRepository.findAll().get(0).getId(), event.getEventId());

        JsonNode payload = objectMapper.readTree(event.getPayload());
        assertEquals(7L, payload.get("userId").asLong());
        assertEquals("STANDARD", payload.get("planCode").asText());
        assertEquals(9, payload.get("months").asInt());
        assertEquals(event.getEventId(), payload.get("eventId").asText());
        assertEquals(1, payload.get("schemaVersion").asInt());
        assertEquals(LocalDateTime.parse(payload.get("paidAt").asText()).plusDays(365),
                LocalDateTime.parse(payload.get("expiresAt").asText()));
        assertEquals(LocalDateTime.parse(payload.get("expiresAt").asText()),
                planValidityRepository.findById(payload.get("orderId").asLong()).orElseThrow().getExpiresAt());
        // Standard plan allocation: GYNECOLOGIST 5, NUTRITIONIST 4.
        assertEquals(2, payload.get("sessions").size());
        int total = 0;
        for (JsonNode allocation : payload.get("sessions")) {
            total += allocation.get("sessionCount").asInt();
        }
        assertEquals(9, total);
    }

    @Test
    void mockSucceed_calledTwice_savesOneEvent() throws Exception {
        Long paymentId = createOrderAndPayment("webhook-key-2");

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/mock/succeed")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/mock/succeed")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());

        assertEquals(1, outboxEventRepository.count());
    }

    @Test
    void mockFail_marksOrderFailed_noEvent() throws Exception {
        Long paymentId = createOrderAndPayment("webhook-key-3");

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/mock/fail")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYMENT_FAILED"));

        assertTrue(outboxEventRepository.findAll().isEmpty());
    }

    @Test
    void directWebhook_badSignature_returns401() throws Exception {
        String body = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"type\":\"PAYMENT_SUCCEEDED\","
                + "\"paymentId\":1,\"providerReference\":\"mock_x\",\"amount\":100,\"occurredAt\":\"2026-01-01T00:00:00\"}";

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", "not-a-real-signature")
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_WEBHOOK_SIGNATURE"));
    }

    @Test
    void directWebhook_sameEventIdTwice_secondIsNoOp() throws Exception {
        Long paymentId = createOrderAndPayment("webhook-key-4");
        String eventId = UUID.randomUUID().toString();
        String body = "{\"eventId\":\"" + eventId + "\",\"type\":\"PAYMENT_SUCCEEDED\","
                + "\"paymentId\":" + paymentId + ",\"providerReference\":\"mock_x\",\"amount\":9999.00,"
                + "\"occurredAt\":\"2026-01-01T00:00:00\"}";
        String signature = sign(body);

        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", signature)
                        .content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Signature", signature)
                        .content(body))
                .andExpect(status().isOk());

        assertEquals(1, paymentWebhookEventRepository.findAll().size());
        assertEquals(1, outboxEventRepository.count());
    }

    private String sign(String rawBody) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
