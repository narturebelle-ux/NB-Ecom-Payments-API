package com.narturebelle.payments.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HealthControllerTest {

    @Test
    void databaseDown_returns503_withoutTheErrorDetail() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new IllegalStateException("password authentication failed for user secret-user"));

        ResponseEntity<Map<String, Object>> response = new HealthController(jdbcTemplate).checkDatabase();

        assertEquals(503, response.getStatusCode().value());
        assertEquals(Map.of("database", "error"), response.getBody());
    }
}
