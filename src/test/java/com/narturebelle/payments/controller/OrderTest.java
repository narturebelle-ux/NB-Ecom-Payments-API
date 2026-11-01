package com.narturebelle.payments.controller;

import com.narturebelle.payments.TestDatabase;
import com.narturebelle.payments.care.CallerProfile;
import com.narturebelle.payments.care.CareDirectory;
import com.narturebelle.payments.model.Role;
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

import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Copied from Care: order creation idempotency, server-side pricing, ownership. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderTest {

    private static final String TOKEN_A = "order-token-a";
    private static final String TOKEN_B = "order-token-b";
    private static final String TOKEN_UNVERIFIED = "order-token-unverified";
    private static final String TOKEN_ADMIN = "order-token-admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OrderRepository orderRepository;

    @MockBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @MockBean
    private CareDirectory careDirectory;

    @BeforeEach
    void setUp() throws Exception {
        TestDatabase.clearOrders(jdbcTemplate);

        when(careDirectory.findCaller("uid-order-a")).thenReturn(Optional.of(new CallerProfile(1L, Set.of(Role.MOTHER))));
        when(careDirectory.findCaller("uid-order-b")).thenReturn(Optional.of(new CallerProfile(2L, Set.of(Role.MOTHER))));
        when(careDirectory.findCaller("uid-order-admin")).thenReturn(Optional.of(new CallerProfile(3L, Set.of(Role.SUPER_ADMIN))));

        when(firebaseTokenVerifier.verify(TOKEN_A))
                .thenReturn(new VerifiedFirebaseToken("uid-order-a", "order-a@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_B))
                .thenReturn(new VerifiedFirebaseToken("uid-order-b", "order-b@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_UNVERIFIED))
                .thenReturn(new VerifiedFirebaseToken("uid-order-a", "order-a@example.com", false, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_ADMIN))
                .thenReturn(new VerifiedFirebaseToken("uid-order-admin", "order-admin@example.com", true, null, null));
    }

    @Test
    void create_missingIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":1,\"paymentMode\":\"ONE_TIME\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void create_unverifiedUser_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + TOKEN_UNVERIFIED)
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":1,\"paymentMode\":\"ONE_TIME\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));
    }

    @Test
    void create_unknownPlan_returns404() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .header("Idempotency-Key", "key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":999999,\"paymentMode\":\"ONE_TIME\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAN_NOT_FOUND"));
    }

    @Test
    void create_success_hasServerComputedPrice() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .header("Idempotency-Key", "key-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":1,\"paymentMode\":\"ONE_TIME\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.planCode").value("STANDARD"))
                .andExpect(jsonPath("$.price").value(9999.00));
    }

    @Test
    void create_installmentMode_generatesSchedule() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .header("Idempotency-Key", "key-installment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":1,\"paymentMode\":\"INSTALLMENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(11998.80))
                .andExpect(jsonPath("$.installmentSchedule", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.installmentSchedule[0].installmentNumber").value(1))
                .andExpect(jsonPath("$.installmentSchedule[1].installmentNumber").value(2));
    }

    @Test
    void create_sameKeyAndBody_returnsSameOrder_noDuplicate() throws Exception {
        String firstId = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .header("Idempotency-Key", "key-repeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":1,\"paymentMode\":\"ONE_TIME\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String secondId = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .header("Idempotency-Key", "key-repeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":1,\"paymentMode\":\"ONE_TIME\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(
                firstId.replaceAll(".*\"id\":(\\d+).*", "$1"),
                secondId.replaceAll(".*\"id\":(\\d+).*", "$1"));
        org.junit.jupiter.api.Assertions.assertEquals(1, orderRepository.count());
    }

    @Test
    void create_sameKeyDifferentPlan_returns409Conflict() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .header("Idempotency-Key", "key-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":1,\"paymentMode\":\"ONE_TIME\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .header("Idempotency-Key", "key-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":2,\"paymentMode\":\"ONE_TIME\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
    }

    @Test
    void getOne_byAnotherUser_returns403_butAdminCanAccess() throws Exception {
        String body = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + TOKEN_A)
                        .header("Idempotency-Key", "key-owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":1,\"paymentMode\":\"ONE_TIME\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long orderId = Long.valueOf(body.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", "Bearer " + TOKEN_B))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/orders/" + orderId).header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId));
    }
}
