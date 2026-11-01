package com.narturebelle.payments.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.narturebelle.payments.TestDatabase;
import com.narturebelle.payments.care.CallerProfile;
import com.narturebelle.payments.care.CareDirectory;
import com.narturebelle.payments.model.Order;
import com.narturebelle.payments.model.OrderStatus;
import com.narturebelle.payments.model.Role;
import com.narturebelle.payments.outbox.OutboxEventRepository;
import com.narturebelle.payments.repository.OrderRepository;
import com.narturebelle.payments.security.FirebaseTokenVerifier;
import com.narturebelle.payments.security.VerifiedFirebaseToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Copied from Care: constant prices, one plan at a time, and a new checkout replacing an unfinished one. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlanPricingTest {

    private static final String TOKEN = "pricing-token";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OutboxEventRepository outboxEventRepository;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;
    @MockBean private CareDirectory careDirectory;

    @BeforeEach
    void setUp() throws Exception {
        TestDatabase.clearOrders(jdbcTemplate);

        when(careDirectory.findCaller("uid-pricing")).thenReturn(Optional.of(new CallerProfile(5L, Set.of(Role.MOTHER))));
        when(careDirectory.pendingConsents(5L)).thenReturn(List.of());
        when(firebaseTokenVerifier.verify(TOKEN))
                .thenReturn(new VerifiedFirebaseToken("uid-pricing", "pricing@example.com", true, null, null));
    }

    private JsonNode quote() throws Exception {
        String body = mockMvc.perform(get("/api/v1/me/plans/quote").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode plan(JsonNode quote, String code) {
        for (JsonNode plan : quote.get("plans")) {
            if (code.equals(plan.get("code").asText())) return plan;
        }
        throw new AssertionError("No plan " + code);
    }

    private long order(long planId, String key) throws Exception {
        String body = mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":" + planId + ",\"paymentMode\":\"ONE_TIME\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private long payment(long orderId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/payments/create").header("Authorization", "Bearer " + TOKEN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":" + orderId + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private void pay(long paymentId) throws Exception {
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/mock/succeed").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    void priceIsTheListedPrice() throws Exception {
        JsonNode quote = quote();
        assertEquals("FULL_PLAN", quote.get("basis").asText());
        assertEquals(9, quote.get("months").asInt());

        JsonNode standard = plan(quote, "STANDARD");
        assertEquals(0, new BigDecimal("9999").compareTo(standard.get("oneTimePrice").decimalValue()));
        assertEquals(0, new BigDecimal("1111").compareTo(standard.get("monthlyPrice").decimalValue()));
        assertEquals(9, standard.get("totalSessions").asInt());
        assertEquals(365, standard.get("validityDays").asInt());

        Order order = orderRepository.findById(order(standard.get("id").asLong(), "pricing-1")).orElseThrow();
        assertEquals(0, new BigDecimal("9999").compareTo(order.getPrice()));
        assertEquals(9, order.getMonths());
    }

    @Test
    void oneActivePlanAtATime() throws Exception {
        long planId = plan(quote(), "STANDARD").get("id").asLong();
        pay(payment(order(planId, "one-plan-1")));

        mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "one-plan-2").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":" + plan(quote(), "PREMIUM").get("id").asLong() + ",\"paymentMode\":\"ONE_TIME\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_ALREADY_ACTIVE"));

        // Once the 12 months are over she can buy again.
        jdbcTemplate.update("update money.plan_validity set expires_at = now() - interval '1 day'");
        order(planId, "one-plan-3");
    }

    @Test
    void aFreeReferralOrderCountsFromItsCreation() throws Exception {
        long planId = plan(quote(), "STANDARD").get("id").asLong();
        Long planVersionId = jdbcTemplate.queryForObject(
                "select id from public.plan_versions where plan_id = ? and active", Long.class, planId);

        Order reward = new Order();
        reward.setUserId(5L);
        reward.setPlanVersionId(planVersionId);
        reward.setIdempotencyKey("referral-reward");
        reward.setRequestFingerprint("fp");
        reward.setStatus(OrderStatus.PAID);
        reward.setPaymentMode(com.narturebelle.payments.model.PaymentMode.ONE_TIME);
        reward.setPrice(BigDecimal.ZERO);
        orderRepository.save(reward);

        mockMvc.perform(post("/api/v1/orders").header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "after-reward").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":" + planId + ",\"paymentMode\":\"ONE_TIME\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_ALREADY_ACTIVE"));
    }

    @Test
    void aNewCheckoutReplacesAnUnfinishedOne_soOnlyOneCanBePaid() throws Exception {
        JsonNode quote = quote();
        long first = order(plan(quote, "STANDARD").get("id").asLong(), "replace-1");
        long firstPayment = payment(first);

        long second = order(plan(quote, "CARE").get("id").asLong(), "replace-2");
        assertEquals(OrderStatus.FAILED, orderRepository.findById(first).orElseThrow().getStatus());
        assertEquals(OrderStatus.PENDING, orderRepository.findById(second).orElseThrow().getStatus());

        // A late success on the replaced order doesn't grant a plan.
        pay(firstPayment);
        assertEquals(OrderStatus.FAILED, orderRepository.findById(first).orElseThrow().getStatus());
        assertEquals(0, outboxEventRepository.count());

        pay(payment(second));
        assertEquals(OrderStatus.PAID, orderRepository.findById(second).orElseThrow().getStatus());
        assertEquals(1, outboxEventRepository.count());
    }
}
