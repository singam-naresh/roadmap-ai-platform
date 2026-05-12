package com.assistant.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.filter.CommonsRequestLoggingFilter;

import java.util.concurrent.Executor;

@Configuration
@EnableCaching
@EnableAsync
@ConfigurationProperties(prefix = "aura.performance")
public class PerformanceConfig {

    private static final Logger log = LoggerFactory.getLogger(PerformanceConfig.class);

    // Configuration properties
    private int corePoolSize = 5;
    private int maxPoolSize = 20;
    private int queueCapacity = 100;
    private String threadNamePrefix = "aura-async-";
    private boolean enableRequestLogging = false;
    private int cacheMaxSize = 1000;
    private long cacheTimeToLive = 3600; // 1 hour in seconds

    /**
     * Async task executor for background processing
     */
    @Bean(name = "aiPipelineExecutor")
    public Executor aiPipelineExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        
        log.info("[performance] AI Pipeline executor configured - Core: {}, Max: {}, Queue: {}", 
                corePoolSize, maxPoolSize, queueCapacity);
        
        return executor;
    }

    /**
     * Cache manager for AI pipeline results
     */
    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(
            "domainAnalysis",
            "qualityScores", 
            "consistencyValidation",
            "specificityAnalysis",
            "roadmapTemplates"
        );
        
        log.info("[performance] Cache manager configured with {} caches", 
                cacheManager.getCacheNames().size());
        
        return cacheManager;
    }

    /**
     * Request logging filter for performance monitoring
     */
    @Bean
    public CommonsRequestLoggingFilter requestLoggingFilter() {
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
        filter.setIncludeClientInfo(true);
        filter.setIncludeQueryString(true);
        filter.setIncludePayload(false); // Don't log request body for security
        filter.setIncludeHeaders(false);
        filter.setMaxPayloadLength(1000);
        filter.setBeforeMessagePrefix("REQUEST: ");
        filter.setAfterMessagePrefix("RESPONSE: ");
        
        if (enableRequestLogging) {
            log.info("[performance] Request logging filter enabled");
        }
        
        return filter;
    }

    /**
     * Performance monitoring aspect
     */
    @Bean
    public PerformanceMonitoringAspect performanceMonitoringAspect() {
        return new PerformanceMonitoringAspect();
    }

    // Getters and setters for configuration properties
    public int getCorePoolSize() { return corePoolSize; }
    public void setCorePoolSize(int corePoolSize) { this.corePoolSize = corePoolSize; }

    public int getMaxPoolSize() { return maxPoolSize; }
    public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }

    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }

    public String getThreadNamePrefix() { return threadNamePrefix; }
    public void setThreadNamePrefix(String threadNamePrefix) { this.threadNamePrefix = threadNamePrefix; }

    public boolean isEnableRequestLogging() { return enableRequestLogging; }
    public void setEnableRequestLogging(boolean enableRequestLogging) { this.enableRequestLogging = enableRequestLogging; }

    public int getCacheMaxSize() { return cacheMaxSize; }
    public void setCacheMaxSize(int cacheMaxSize) { this.cacheMaxSize = cacheMaxSize; }

    public long getCacheTimeToLive() { return cacheTimeToLive; }
    public void setCacheTimeToLive(long cacheTimeToLive) { this.cacheTimeToLive = cacheTimeToLive; }
}