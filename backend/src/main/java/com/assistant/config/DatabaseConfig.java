package com.assistant.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Database configuration for proper H2 shutdown handling.
 * Ensures graceful database closure to prevent file locking issues.
 */
@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Autowired
    private DataSource dataSource;

    /**
     * Gracefully shutdown H2 database when Spring context is closed.
     * This prevents file locking issues on restart.
     */
    @EventListener
    public void handleContextClosed(ContextClosedEvent event) {
        log.info("[db] Gracefully shutting down H2 database...");
        try (Connection connection = dataSource.getConnection()) {
            // Execute H2 SHUTDOWN command to properly close the database
            connection.createStatement().execute("SHUTDOWN");
            log.info("[db] H2 database shutdown completed successfully");
        } catch (SQLException e) {
            log.warn("[db] Error during H2 shutdown (this is usually harmless): {}", e.getMessage());
        }
    }
}