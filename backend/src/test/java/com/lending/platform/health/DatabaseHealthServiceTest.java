package com.lending.platform.health;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseHealthServiceTest {
    @Test
    void reportsDatabaseUpWhenSelectOneSucceeds() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        assertTrue(new DatabaseHealthService(jdbcTemplate).isDatabaseUp());
    }

    @Test
    void reportsDatabaseDownWhenQueryFails() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenThrow(new IllegalStateException("unavailable"));

        assertFalse(new DatabaseHealthService(jdbcTemplate).isDatabaseUp());
    }
}
