package com.assistant.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Runs after Spring Boot fully starts and verifies the database schema is healthy.
 * Logs a clear success or failure message so startup issues are immediately visible.
 */
@Component
public class DatabaseHealthCheck {

    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthCheck.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseHealthCheck(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifySchema() {
        try {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tasks", Integer.class);
            log.info("✅ [db] Schema OK — tasks table exists with {} row(s)", count);
        } catch (Exception e) {
            log.error("❌ [db] SCHEMA FAILURE — tasks table not found: {}", e.getMessage());
            log.error("❌ [db] Check application.properties datasource config and ensure ddl-auto=update is set");
        }
    }
}
