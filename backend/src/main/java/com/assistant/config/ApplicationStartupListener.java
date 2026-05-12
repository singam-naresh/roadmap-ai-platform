package com.assistant.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class ApplicationStartupListener {

    private static final Logger log = LoggerFactory.getLogger(ApplicationStartupListener.class);
    private static final String PID_FILE = "./data/aura-os.pid";

    private final Environment environment;

    public ApplicationStartupListener(Environment environment) {
        this.environment = environment;
    }

    @EventListener
    public void onApplicationReady(ApplicationReadyEvent event) {
        String port = environment.getProperty("server.port", "8080");
        String profile = String.join(",", environment.getActiveProfiles());

        // Explicit port log  helps Render confirm the port is open
        System.out.println("=================================================");
        System.out.println("SERVER STARTED ON PORT: " + port);
        System.out.println("ACTIVE PROFILE: " + profile);
        System.out.println("HEALTH CHECK: http://0.0.0.0:" + port + "/api/health");
        System.out.println("=================================================");

        log.info("[startup] Roadmap AI Platform started on port {} (profile: {})", port, profile);

        // Write PID file (non-critical  failure is harmless)
        try {
            Path dataDir = Paths.get("./data");
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
            }
            String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            try (FileWriter writer = new FileWriter(PID_FILE)) {
                writer.write(pid);
            }
        } catch (IOException e) {
            log.debug("[startup] Could not write PID file: {}", e.getMessage());
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Files.deleteIfExists(Paths.get(PID_FILE));
            } catch (IOException ignored) {}
        }));
    }
}