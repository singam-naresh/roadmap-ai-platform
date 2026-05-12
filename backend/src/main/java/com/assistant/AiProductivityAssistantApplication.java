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
        // Load .env file from the project root (backend/.env) before Spring starts.
        // This means you never need to set env vars manually in your IDE or terminal.
        loadDotEnv();
        SpringApplication.run(AiProductivityAssistantApplication.class, args);
    }

    /**
     * Reads backend/.env and sets each KEY=VALUE as a system property
     * so Spring Boot's ${KEY} placeholders resolve correctly.
     *
     * Rules:
     * - Skips blank lines and lines starting with #
     * - Does NOT override variables already set in the real environment
     *   (so production env vars always win over the .env file)
     */
    private static void loadDotEnv() {
        // Look for .env next to the jar (production) or in the backend/ directory (dev)
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

                    // Strip surrounding quotes if present
                    if (value.startsWith("\"") && value.endsWith("\"") && value.length() > 1) {
                        value = value.substring(1, value.length() - 1);
                    }

                    // Only set if not already defined in the real environment
                    if (System.getenv(key) == null && System.getProperty(key) == null) {
                        System.setProperty(key, value);
                        loaded.put(key, key.toLowerCase().contains("key") ? "***" : value);
                    }
                }

                if (!loaded.isEmpty()) {
                    System.out.println("[.env] Loaded " + loaded.size() + " variable(s) from " + file.getAbsolutePath());
                    loaded.forEach((k, v) -> System.out.println("[.env]   " + k + " = " + v));
                }
                return; // stop after first .env found

            } catch (Exception e) {
                System.err.println("[.env] Failed to read " + file.getAbsolutePath() + ": " + e.getMessage());
            }
        }
    }
}
