package com.narturebelle.payments.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * On real Tomcat over a raw socket: MockMvc doesn't decode paths the same way, and URI clients can rewrite them.
 * No spelling of an /internal path may reach an endpoint without the private token.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InternalPathBypassTest {

    private static final String REST = "v1/users/7/order-summary";

    @LocalServerPort
    private int port;

    private String get(String target, String extraHeader) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            OutputStream out = socket.getOutputStream();
            out.write(("GET " + target + " HTTP/1.1\r\nHost: localhost\r\n" + extraHeader + "Connection: close\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            return new String(socket.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/internal/" + REST,
            "/%69nternal/" + REST,
            "/%69%6e%74%65%72%6e%61%6c/" + REST,
            "/INTERNAL/" + REST,
            "//internal/" + REST,
            "///internal/" + REST,
            "/x/../internal/" + REST,
            "/./internal/" + REST,
            "/%2e/internal/" + REST,
            "/x/%2e%2e/internal/" + REST,
            "/internal;a=b/" + REST,
            "/%69nternal;a=b/" + REST,
            "/;a=b/internal/" + REST,
            "/internal/./" + REST,
            "/internal//" + REST,
            "/internal/%76%31/users/7/order-summary",
    })
    void noSpellingReachesTheEndpointWithoutTheToken(String target) throws IOException {
        String response = get(target, "");

        assertFalse(response.contains("hasPaidOrder"), target + " reached the endpoint: " + response);
        assertFalse(response.startsWith("HTTP/1.1 200"), target + " returned 200: " + response);
    }

    /** Proves the requests above are well formed: the same socket with the token does reach it. */
    @Test
    void theTokenStillGetsThrough() throws IOException {
        String response = get("/internal/" + REST, "X-Internal-Token: test-internal-token\r\n");

        assertTrue(response.startsWith("HTTP/1.1 200"), response);
        assertTrue(response.contains("hasPaidOrder"), response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/%61dmin/finance/summary", "/api/v1/admin/finance/summary", "//api/v1/admin/finance/summary"})
    void adminPathsAreNotOpenEitherWhenEncoded(String target) throws IOException {
        String response = get(target, "");

        assertFalse(response.contains("grossRevenue"), target + " reached the endpoint: " + response);
        assertEquals(false, response.startsWith("HTTP/1.1 200"), target + " returned 200");
    }
}
