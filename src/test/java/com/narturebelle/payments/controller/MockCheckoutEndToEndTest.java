package com.narturebelle.payments.controller;

import com.narturebelle.payments.TestDatabase;
import com.narturebelle.payments.care.CallerProfile;
import com.narturebelle.payments.care.CareDirectory;
import com.narturebelle.payments.model.Role;
import com.narturebelle.payments.security.FirebaseTokenVerifier;
import com.narturebelle.payments.security.VerifiedFirebaseToken;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/** One mock checkout over a real port: order, payment, webhook, then the paid order and its saved event. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MockCheckoutEndToEndTest {

    private static final String TOKEN = "checkout-token";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @MockBean
    private CareDirectory careDirectory;

    @Test
    @SuppressWarnings("unchecked")
    void mockCheckout_paysTheOrderAndSavesOneEvent() throws Exception {
        TestDatabase.clearOrders(jdbcTemplate);
        when(careDirectory.findCaller("uid-checkout")).thenReturn(Optional.of(new CallerProfile(7L, Set.of(Role.MOTHER))));
        when(firebaseTokenVerifier.verify(TOKEN))
                .thenReturn(new VerifiedFirebaseToken("uid-checkout", "checkout@example.com", true, null, null));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpHeaders orderHeaders = new HttpHeaders(headers);
        orderHeaders.set("Idempotency-Key", "end-to-end");
        Map<String, Object> order = rest.postForObject("/api/v1/orders",
                new HttpEntity<>("{\"planId\":1,\"paymentMode\":\"ONE_TIME\"}", orderHeaders), Map.class);
        assertEquals("PENDING", order.get("status"));

        Map<String, Object> payment = rest.postForObject("/api/v1/payments/create",
                new HttpEntity<>("{\"orderId\":" + order.get("id") + "}", headers), Map.class);

        ResponseEntity<Map> paid = rest.postForEntity("/api/v1/payments/" + payment.get("id") + "/mock/succeed",
                new HttpEntity<>(headers), Map.class);
        assertEquals(HttpStatus.OK, paid.getStatusCode());

        Map<String, Object> after = rest.exchange("/api/v1/orders/" + order.get("id"), HttpMethod.GET,
                new HttpEntity<>(headers), Map.class).getBody();
        assertEquals("PAID", after.get("status"));

        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from money.outbox_events where event_id = ?", Integer.class,
                "payment-succeeded:" + order.get("id")));
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from money.plan_validity where order_id = ?", Integer.class,
                ((Number) order.get("id")).longValue()));
    }
}
