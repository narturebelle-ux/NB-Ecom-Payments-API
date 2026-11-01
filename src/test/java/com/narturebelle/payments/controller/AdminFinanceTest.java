package com.narturebelle.payments.controller;

import com.narturebelle.payments.TestDatabase;
import com.narturebelle.payments.care.CallerProfile;
import com.narturebelle.payments.care.CareDirectory;
import com.narturebelle.payments.model.Order;
import com.narturebelle.payments.model.OrderStatus;
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

/** Copied from Care: admin finance summary and admin-entered refunds. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminFinanceTest {

    private static final String TOKEN_USER = "finance-token-user";
    private static final String TOKEN_ADMIN = "finance-token-admin";

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

    private Long paidOrderId;

    @BeforeEach
    void setUp() throws Exception {
        TestDatabase.clearOrders(jdbcTemplate);

        when(careDirectory.findCaller("uid-finance-user")).thenReturn(Optional.of(new CallerProfile(1L, Set.of(Role.MOTHER))));
        when(careDirectory.findCaller("uid-finance-admin")).thenReturn(Optional.of(new CallerProfile(2L, Set.of(Role.FINANCE_ADMIN))));

        when(firebaseTokenVerifier.verify(TOKEN_USER))
                .thenReturn(new VerifiedFirebaseToken("uid-finance-user", "finance-user@example.com", true, null, null));
        when(firebaseTokenVerifier.verify(TOKEN_ADMIN))
                .thenReturn(new VerifiedFirebaseToken("uid-finance-admin", "finance-admin@example.com", true, null, null));

        String orderBody = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + TOKEN_USER)
                        .header("Idempotency-Key", "finance-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":1,\"paymentMode\":\"ONE_TIME\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        paidOrderId = Long.valueOf(orderBody.replaceAll(".*\"id\":(\\d+).*", "$1"));

        String paymentBody = mockMvc.perform(post("/api/v1/payments/create")
                        .header("Authorization", "Bearer " + TOKEN_USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + paidOrderId + "}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Long paymentId = Long.valueOf(paymentBody.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/mock/succeed")
                        .header("Authorization", "Bearer " + TOKEN_USER))
                .andExpect(status().isOk());
    }

    @Test
    void nonAdmin_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/finance/summary").header("Authorization", "Bearer " + TOKEN_USER))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_seesCorrectSummaryForOnePaidOrder() throws Exception {
        mockMvc.perform(get("/api/v1/admin/finance/summary").header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidOrderCount").value(1))
                .andExpect(jsonPath("$.grossRevenue").value(9999.00))
                .andExpect(jsonPath("$.totalRefunded").value(0))
                .andExpect(jsonPath("$.netRevenue").value(9999.00))
                .andExpect(jsonPath("$.deferredRevenueLiability").value(9999.00));
    }

    @Test
    void partialRefund_updatesOrderStatusAndSummary() throws Exception {
        mockMvc.perform(post("/api/v1/admin/orders/" + paidOrderId + "/refunds")
                        .header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":3000.00,\"reason\":\"Partial refund per customer request\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(3000.00));

        mockMvc.perform(get("/api/v1/admin/finance/summary").header("Authorization", "Bearer " + TOKEN_ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRefunded").value(3000.00))
                .andExpect(jsonPath("$.netRevenue").value(6999.00));

        mockMvc.perform(get("/api/v1/orders/" + paidOrderId).header("Authorization", "Bearer " + TOKEN_USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_REFUNDED"));

        Integer refundEvents = jdbcTemplate.queryForObject(
                "select count(*) from money.outbox_events where event_type = 'RefundIssued' and payload like '%\"orderStatus\":\"PARTIALLY_REFUNDED\"%'",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, refundEvents);
    }

    @Test
    void refundExceedingRemaining_returns409() throws Exception {
        mockMvc.perform(post("/api/v1/admin/orders/" + paidOrderId + "/refunds")
                        .header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":99999.00,\"reason\":\"Too much\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_NOT_ALLOWED"));
    }

    @Test
    void refundOnPendingOrder_returns409() throws Exception {
        // Saved directly: she already owns a paid plan here, so POST /orders would refuse a second one.
        Order paid = orderRepository.findById(paidOrderId).orElseThrow();
        Order pending = new Order();
        pending.setUserId(paid.getUserId());
        pending.setPlanVersionId(paid.getPlanVersionId());
        pending.setIdempotencyKey("finance-key-pending");
        pending.setRequestFingerprint("fp");
        pending.setStatus(OrderStatus.PENDING);
        pending.setPaymentMode(paid.getPaymentMode());
        pending.setPrice(paid.getPrice());
        Long pendingOrderId = orderRepository.save(pending).getId();

        mockMvc.perform(post("/api/v1/admin/orders/" + pendingOrderId + "/refunds")
                        .header("Authorization", "Bearer " + TOKEN_ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00,\"reason\":\"Never paid\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_NOT_ALLOWED"));
    }
}
