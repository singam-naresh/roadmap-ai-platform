package com.assistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
public class AiProductivityAssistantApplication {

    public static void main(String[] args) {
        // Load .env for local development only.
        // In production (Render/Docker), real env vars are already set
        // and loadDotEnv() will not override them.
        loadDotEnv();

        // Log the port before Spring starts so Render can detect it early
        String port = System.getenv("PORT");
        if (port == null) port = System.getProperty("PORT", "8080");
        System.out.println("[startup] Binding to port: " + port);
        System.out.println("[startup] Profile: " + System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE", "dev"));

        SpringApplication.run(AiProductivityAssistantApplication.class, args);
    }

    private static void loadDotEnv() {
        String[] candidates = { ".env", "backend/.env", "../.env" };

        for (String candidate : candidates) {
            File file = new File(candidate);
            if (!file.exists()) continue;

            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                Map<String, String> loaded = new HashMap<>();

                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;

                    int eq = line.indexOf('=');
                    if (eq < 1) continue;

                    String key   = line.substring(0, eq).trim();
                    String value = line.substring(eq + 1).trim();

                    if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
                        value = value.substring(1, value.length() - 1);
                    }

                    // Never override real environment variables
                    if (System.getenv(key) == null && System.getProperty(key) == null) {
                        System.setProperty(key, value);
                        loaded.put(key, key.toLowerCase().contains("key") || key.toLowerCase().contains("secret") ? "***" : value);
                    }
                }

                if (!loaded.isEmpty()) {
                    System.out.println("[.env] Loaded " + loaded.size() + " variable(s) from " + file.getAbsolutePath());
                }
                return;

            } catch (Exception e) {
                System.err.println("[.env] Could not read " + candidate + ": " + e.getMessage());
            }
        }
    }
}