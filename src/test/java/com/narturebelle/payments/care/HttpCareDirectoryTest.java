package com.narturebelle.payments.care;

import com.narturebelle.payments.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpCareDirectoryTest {

    private static final String BY_UID = "http://care/internal/v1/users/by-firebase-uid/uid-1";

    private MockRestServiceServer server;
    private HttpCareDirectory directory;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://care")
                .defaultHeader("X-Internal-Token", "secret");
        server = MockRestServiceServer.bindTo(builder).build();
        directory = new HttpCareDirectory(builder.build());
    }

    @Test
    void findCaller_readsIdentity_sendsToken_andCachesIt() {
        server.expect(ExpectedCount.once(), requestTo(BY_UID))
                .andExpect(header("X-Internal-Token", "secret"))
                .andRespond(withSuccess("{\"userId\":15,\"roles\":[\"MOTHER\",\"FINANCE_ADMIN\"]}", MediaType.APPLICATION_JSON));

        CallerProfile expected = new CallerProfile(15L, Set.of(Role.MOTHER, Role.FINANCE_ADMIN));
        assertEquals(Optional.of(expected), directory.findCaller("uid-1"));
        assertEquals(Optional.of(expected), directory.findCaller("uid-1"));
        server.verify();
    }

    @Test
    void findCaller_noProfileYet_isEmptyAndNotCached() {
        server.expect(ExpectedCount.twice(), requestTo(BY_UID)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertEquals(Optional.empty(), directory.findCaller("uid-1"));
        assertEquals(Optional.empty(), directory.findCaller("uid-1"));
        server.verify();
    }

    @Test
    void findCaller_unknownRoleIsIgnored() {
        server.expect(requestTo(BY_UID))
                .andRespond(withSuccess("{\"userId\":15,\"roles\":[\"MOTHER\",\"BRAND_NEW_ROLE\"]}", MediaType.APPLICATION_JSON));

        assertEquals(Set.of(Role.MOTHER), directory.findCaller("uid-1").orElseThrow().roles());
    }

    @Test
    void findCaller_careError_isUnavailable() {
        server.expect(requestTo(BY_UID)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThrows(CareUnavailableException.class, () -> directory.findCaller("uid-1"));
    }

    @Test
    void findCaller_refusedToken_isUnavailable() {
        server.expect(requestTo(BY_UID)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertThrows(CareUnavailableException.class, () -> directory.findCaller("uid-1"));
    }

    @Test
    void findCaller_connectionFailure_isUnavailable() {
        server.expect(requestTo(BY_UID)).andRespond(request -> { throw new IOException("connection refused"); });
        assertThrows(CareUnavailableException.class, () -> directory.findCaller("uid-1"));
    }

    @Test
    void pendingConsents_readsTheList() {
        server.expect(requestTo("http://care/internal/v1/users/15/consent-status"))
                .andRespond(withSuccess("{\"pending\":[\"PRIVACY\",\"TERMS\"]}", MediaType.APPLICATION_JSON));

        assertEquals(List.of("PRIVACY", "TERMS"), directory.pendingConsents(15L));
    }

    @Test
    void pendingConsents_careError_isUnavailable_notGranted() {
        server.expect(requestTo("http://care/internal/v1/users/15/consent-status"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThrows(CareUnavailableException.class, () -> directory.pendingConsents(15L));
    }
}
