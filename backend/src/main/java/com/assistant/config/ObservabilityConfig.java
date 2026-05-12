package com.assistant.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class ObservabilityConfig {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityConfig.class);

    /**
     * Simplified metrics for AI pipeline performance monitoring
     */
    @Component
    public static class AIPipelineMetrics {
        
        private final MeterRegistry meterRegistry;
        private final AtomicLong activeGenerations;

        public AIPipelineMetrics(MeterRegistry meterRegistry) {
            this.meterRegistry = meterRegistry;
            this.activeGenerations = new AtomicLong(0);
            log.info("[observability] AI Pipeline metrics initialized");
        }

        public Timer.Sample startRoadmapGeneration() {
            activeGenerations.incrementAndGet();
            Counter.builder("aura.ai.roadmap.generation.total")
                    .description("Total number of roadmap generations")
                    .register(meterRegistry)
                    .increment();
            return Timer.start(meterRegistry);
        }

        public void endRoadmapGeneration(Timer.Sample sample) {
            Timer.builder("aura.ai.roadmap.generation.duration")
                    .description("Time taken to generate roadmaps")
                    .register(meterRegistry);
            sample.stop(Timer.builder("aura.ai.roadmap.generation.duration").register(meterRegistry));
            activeGenerations.decrementAndGet();
        }

        public void recordValidationFailure(String reason) {
            Counter.builder("aura.ai.roadmap.validation.failures")
                    .tag("reason", reason)
                    .register(meterRegistry)
                    .increment();
        }

        public void recordDomainDetection(String domain, double confidence) {
            Counter.builder("aura.ai.domain.detection.total")
                    .tag("domain", domain)
                    .register(meterRegistry)
                    .increment();
        }

        public void recordQualityScore(String domain, double score, String level) {
            Counter.builder("aura.ai.quality.score.total")
                    .tag("domain", domain)
                    .tag("level", level)
                    .register(meterRegistry)
                    .increment();
        }

        public void recordConsistencyValidation(String profile, double score, boolean consistent) {
            Counter.builder("aura.ai.consistency.validation.total")
                    .tag("profile", profile)
                    .tag("consistent", String.valueOf(consistent))
                    .register(meterRegistry)
                    .increment();
        }

        public void recordSpecificityAnalysis(String domain, double score, int genericCount, int concreteCount) {
            Counter.builder("aura.ai.specificity.analysis.total")
                    .tag("domain", domain)
                    .register(meterRegistry)
                    .increment();
        }

        public void recordRelationshipAnalysis(String domain, double coherenceScore, int conflictCount, int missingDependencies) {
            Counter.builder("aura.ai.relationship.analysis.total")
                    .tag("domain", domain)
                    .register(meterRegistry)
                    .increment();
        }

        public void recordGroqApiCall(long durationMs, boolean success) {
            Timer.builder("aura.ai.groq.api.duration")
                    .description("Groq API call duration")
                    .tag("success", String.valueOf(success))
                    .register(meterRegistry)
                    .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        public void recordCacheHit(String cacheType, boolean hit) {
            Counter.builder("aura.ai.cache.access")
                    .description("Cache access statistics")
                    .tag("type", cacheType)
                    .tag("hit", String.valueOf(hit))
                    .register(meterRegistry)
                    .increment();
        }

        public long getActiveGenerations() {
            return activeGenerations.get();
        }
    }
}