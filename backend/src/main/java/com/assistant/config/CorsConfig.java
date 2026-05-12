package com.assistant.config;

/**
 * CORS is handled entirely by SecurityConfig.corsConfigurationSource().
 *
 * This class is intentionally empty — do not add WebMvcConfigurer CORS here.
 * Having two CORS configurations (SecurityConfig + WebMvcConfigurer) causes
 * Spring MVC to fall through to the static resource handler, breaking
 * controller mappings like /api/health.
 *
 * See SecurityConfig.corsConfigurationSource() for the active CORS config.
 */
public class CorsConfig {
    // Intentionally empty — CORS handled by SecurityConfig
}
