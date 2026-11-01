package com.narturebelle.payments.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.narturebelle.payments.TestDatabase;
import com.narturebelle.payments.care.CallerProfile;
import com.narturebelle.payments.care.CareDirectory;
import com.narturebelle.payments.care.CareUnavailableException;
import com.narturebelle.payments.model.Role;
import com.narturebelle.payments.repository.OrderRepository;
import com.narturebelle.payments.repository.PlanValidityRepository;
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

import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The private endpoints Care calls, and how a Care outage reaches a signed-in caller. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalMoneyTest {

    private static final String INTERNAL = "test-internal-token";
    private static final String TOKEN = "internal-test-user-token";
    private static final String REWARD = "{\"rewardKey\":\"referral-reward-7\",\"userId\":7,\"planCode\":\"PREMIUM\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PlanValidityRepository planValidityRepository;

    @MockBean private FirebaseTokenVerifier firebaseTokenVerifier;
    @MockBean private CareDirectory careDirectory;

    @BeforeEach
    void setUp() throws Exception {
        TestDatabase.clearOrders(jdbcTemplate);
        when(firebaseTokenVerifier.verify(TOKEN))
                .thenReturn(new VerifiedFirebaseToken("uid-internal", "internal@example.com", true, null, null));
        when(careDirectory.findCaller("uid-internal")).thenReturn(Optional.of(new CallerProfile(7L, Set.of(Role.SUPER_ADMIN))));
    }

    private String reward() throws Exception {
        return mockMvc.perform(post("/internal/v1/orders/reward").header("X-Internal-Token", INTERNAL)
                        .contentType(MediaType.APPLICATION_JSON).content(REWARD))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void noToken_wrongToken_orUserTokenAreRefused() throws Exception {
        mockMvc.perform(get("/internal/v1/users/7/order-summary"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/v1/users/7/order-summary").header("X-Internal-Token", "wrong"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/v1/users/7/order-summary").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reward_createsAFreePaidOrder_withFullSessionsAndStoredExpiry() throws Exception {
        JsonNode reward = objectMapper.readTree(reward());

        assertEquals("PREMIUM", reward.get("planCode").asText());
        int total = 0;
        for (JsonNode allocation : reward.get("sessions")) {
            total += allocation.get("sessionCount").asInt();
        }
        assertEquals(16, total);

        long orderId = reward.get("orderId").asLong();
        assertEquals("PAID", orderRepository.findById(orderId).orElseThrow().getStatus().name());
        assertEquals(0, orderRepository.findById(orderId).orElseThrow().getPrice().signum());
        assertEquals(1, planValidityRepository.count());
    }

    @Test
    void reward_repeated_returnsTheSameOrderAndExpiry() throws Exception {
        JsonNode first = objectMapper.readTree(reward());
        JsonNode second = objectMapper.readTree(reward());

        assertEquals(first.get("orderId").asLong(), second.get("orderId").asLong());
        assertEquals(first.get("expiresAt").asText(), second.get("expiresAt").asText());
        assertEquals(1, orderRepository.count());
    }

    @Test
    void reward_grantedBeforeTheMove_isReusedByItsKey() throws Exception {
        Long planVersionId = jdbcTemplate.queryForObject(
                "select v.id from public.plan_versions v join public.plans p on p.id = v.plan_id where p.code = 'PREMIUM' and v.active", Long.class);
        Long existing = jdbcTemplate.queryForObject(
                "insert into public.orders (user_id, plan_version_id, idempotency_key, request_fingerprint, status, payment_mode, price, months, created_at, updated_at) "
                        + "values (7, ?, 'referral-reward-7', 'referral-reward', 'PAID', 'ONE_TIME', 0, 9, now(), now()) returning id", Long.class, planVersionId);

        assertEquals(existing, objectMapper.readTree(reward()).get("orderId").asLong());
        assertEquals(1, orderRepository.count());
    }

    @Test
    void orderSummary_tellsApartAnyPaidAndPaidAboveZero() throws Exception {
        mockMvc.perform(get("/internal/v1/users/7/order-summary").header("X-Internal-Token", INTERNAL))
                .andExpect(jsonPath("$.hasAnyOrder").value(false))
                .andExpect(jsonPath("$.hasPaidOrder").value(false))
                .andExpect(jsonPath("$.hasPaidOrderAboveZero").value(false));

        reward();

        mockMvc.perform(get("/internal/v1/users/7/order-summary").header("X-Internal-Token", INTERNAL))
                .andExpect(jsonPath("$.hasAnyOrder").value(true))
                .andExpect(jsonPath("$.hasPaidOrder").value(true))
                .andExpect(jsonPath("$.hasPaidOrderAboveZero").value(false));
    }

    @Test
    void moneyExport_hasAllFourLists() throws Exception {
        reward();

        mockMvc.perform(get("/internal/v1/users/7/money-export").header("X-Internal-Token", INTERNAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders", hasSize(1)))
                .andExpect(jsonPath("$.payments", hasSize(0)))
                .andExpect(jsonPath("$.refunds", hasSize(0)))
                .andExpect(jsonPath("$.installmentSchedules", hasSize(0)));
    }

    @Test
    void careOutageDuringSignIn_returns503_not401() throws Exception {
        when(careDirectory.findCaller("uid-internal")).thenThrow(new CareUnavailableException("down", null));

        mockMvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void careOutageDuringConsentCheck_returns503() throws Exception {
        when(careDirectory.pendingConsents(7L)).thenThrow(new CareUnavailableException("down", null));

        mockMvc.perform(get("/api/v1/me/plans/quote").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }
}
