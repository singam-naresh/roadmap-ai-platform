package com.assistant.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Application startup listener to prevent duplicate instances and manage PID file.
 */
@Component
public class ApplicationStartupListener {

    private static final Logger log = LoggerFactory.getLogger(ApplicationStartupListener.class);
    private static final String PID_FILE = "./data/aura-os.pid";

    @EventListener
    public void onApplicationReady(ApplicationReadyEvent event) {
        String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        
        try {
            // Ensure data directory exists
            Path dataDir = Paths.get("./data");
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
            }

            // Write current PID to file
            try (FileWriter writer = new FileWriter(PID_FILE)) {
                writer.write(pid);
            }
            
            log.info("[startup] Adaptive AI Learning Roadmap Platform started successfully with PID: {}", pid);
            log.info("[startup] PID file created at: {}", new File(PID_FILE).getAbsolutePath());
            
        } catch (IOException e) {
            log.warn("[startup] Could not create PID file: {}", e.getMessage());
        }

        // Add shutdown hook to clean up PID file
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Files.deleteIfExists(Paths.get(PID_FILE));
                log.info("[shutdown] PID file cleaned up");
            } catch (IOException e) {
                log.warn("[shutdown] Could not delete PID file: {}", e.getMessage());
            }
        }));
    }
}