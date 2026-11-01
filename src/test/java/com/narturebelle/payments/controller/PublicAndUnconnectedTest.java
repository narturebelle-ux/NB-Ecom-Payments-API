package com.narturebelle.payments.controller;

import com.narturebelle.payments.security.FirebaseTokenVerifier;
import com.narturebelle.payments.security.VerifiedFirebaseToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Public endpoints work, and with the real UnconnectedCareDirectory a valid token gets nothing else. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicAndUnconnectedTest {

    private static final String TOKEN = "unconnected-token";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @BeforeEach
    void setUp() throws Exception {
        when(firebaseTokenVerifier.verify(TOKEN))
                .thenReturn(new VerifiedFirebaseToken("uid-anyone", "anyone@example.com", true, null, null));
    }

    @Test
    void hello_isPublic() throws Exception {
        mockMvc.perform(get("/api/v1/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello Payments"));
    }

    @Test
    void listPlans_noAuthNeeded_returnsSeededTiers() throws Exception {
        mockMvc.perform(get("/api/v1/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("STANDARD", "CARE", "PREMIUM")))
                .andExpect(jsonPath("$[0].code").value("STANDARD"))
                .andExpect(jsonPath("$[0].oneTimePrice").value(9999.00))
                .andExpect(jsonPath("$[0].totalSessions").value(9))
                .andExpect(jsonPath("$[0].allocations").isArray());
    }

    @Test
    void getOnePlan_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/plans/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getOnePlan_knownPlan_returnsAllocationDetail() throws Exception {
        mockMvc.perform(get("/api/v1/plans/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.allocations").isArray());
    }

    @Test
    void healthDb_isPublic() throws Exception {
        mockMvc.perform(get("/health/db"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.database").value("ok"));
    }

    @Test
    void noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void order_validTokenButUnknownToCare_isRefused() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", "Bearer " + TOKEN)
                        .header("Idempotency-Key", "unconnected")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planId\":1,\"paymentMode\":\"ONE_TIME\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void admin_validTokenButUnknownToCare_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/finance/summary").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void quote_consentCannotBeConfirmed_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/me/plans/quote").header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CONSENT_REQUIRED"));
    }
}
