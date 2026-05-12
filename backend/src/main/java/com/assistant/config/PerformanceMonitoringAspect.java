package com.assistant.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Aspect
@Component
public class PerformanceMonitoringAspect {

    private static final Logger log = LoggerFactory.getLogger(PerformanceMonitoringAspect.class);

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    /**
     * Annotation to mark methods for performance monitoring
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MonitorPerformance {
        String value() default "";
        boolean logExecution() default true;
        boolean recordMetrics() default true;
    }

    /**
     * Around advice for methods annotated with @MonitorPerformance only.
     * Removed broad service/engine/pipeline/controller/repository pointcuts
     * to avoid overhead on every trivial method call.
     */
    @Around("@annotation(monitorPerformance)")
    public Object monitorAnnotatedMethods(ProceedingJoinPoint joinPoint, MonitorPerformance monitorPerformance) throws Throwable {
        String metricName = monitorPerformance.value().isEmpty() ?
            joinPoint.getSignature().toShortString() : monitorPerformance.value();
        return monitorExecution(joinPoint, "annotated", metricName,
                              monitorPerformance.logExecution(), monitorPerformance.recordMetrics());
    }

    private Object monitorExecution(ProceedingJoinPoint joinPoint, String category) throws Throwable {
        return monitorExecution(joinPoint, category, null, true, true);
    }

    private Object monitorExecution(ProceedingJoinPoint joinPoint, String category, 
                                  String customMetricName, boolean logExecution, boolean recordMetrics) throws Throwable {
        
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String fullMethodName = className + "." + methodName;
        
        long startTime = System.currentTimeMillis();
        Timer.Sample sample = null;
        
        // Start metrics recording if enabled
        if (recordMetrics && meterRegistry != null) {
            String metricName = customMetricName != null ? customMetricName : 
                "aura." + category + ".execution.duration";
            sample = Timer.start(meterRegistry);
        }

        try {
            if (logExecution) {
                log.debug("[performance] Starting execution: {} (category: {})", fullMethodName, category);
            }

            Object result = joinPoint.proceed();
            
            long duration = System.currentTimeMillis() - startTime;
            
            if (logExecution) {
                if (duration > 1000) {
                    log.warn("[performance] SLOW execution: {} took {}ms (category: {})", 
                            fullMethodName, duration, category);
                } else if (duration > 100) {
                    log.info("[performance] Completed execution: {} took {}ms (category: {})", 
                            fullMethodName, duration, category);
                } else {
                    log.debug("[performance] Completed execution: {} took {}ms (category: {})", 
                            fullMethodName, duration, category);
                }
            }

            // Record success metrics
            if (recordMetrics && meterRegistry != null) {
                if (sample != null) {
                    String metricName = customMetricName != null ? customMetricName : 
                        "aura." + category + ".execution.duration";
                    sample.stop(Timer.builder(metricName)
                            .tag("class", className)
                            .tag("method", methodName)
                            .tag("status", "success")
                            .register(meterRegistry));
                }

                // Record method-specific counters
                meterRegistry.counter("aura." + category + ".execution.count",
                        "class", className,
                        "method", methodName,
                        "status", "success")
                        .increment();
            }

            return result;

        } catch (Throwable throwable) {
            long duration = System.currentTimeMillis() - startTime;
            
            if (logExecution) {
                log.error("[performance] Failed execution: {} took {}ms (category: {}) - Error: {}", 
                        fullMethodName, duration, category, throwable.getMessage());
            }

            // Record failure metrics
            if (recordMetrics && meterRegistry != null) {
                if (sample != null) {
                    String metricName = customMetricName != null ? customMetricName : 
                        "aura." + category + ".execution.duration";
                    sample.stop(Timer.builder(metricName)
                            .tag("class", className)
                            .tag("method", methodName)
                            .tag("status", "error")
                            .register(meterRegistry));
                }

                meterRegistry.counter("aura." + category + ".execution.count",
                        "class", className,
                        "method", methodName,
                        "status", "error")
                        .increment();

                meterRegistry.counter("aura." + category + ".execution.errors",
                        "class", className,
                        "method", methodName,
                        "error", throwable.getClass().getSimpleName())
                        .increment();
            }

            throw throwable;
        }
    }

    /**
     * Monitor external Groq API calls specifically.
     */
    @Around("execution(* com.assistant.service.GroqClient.*(..))")
    public Object monitorGroqApiCalls(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        long startTime = System.currentTimeMillis();
        
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("[performance] Groq API call: {} completed in {}ms", methodName, duration);
            
            if (meterRegistry != null) {
                meterRegistry.timer("aura.groq.api.duration",
                        "method", methodName,
                        "status", "success")
                        .record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
            
            return result;
            
        } catch (Throwable throwable) {
            long duration = System.currentTimeMillis() - startTime;
            
            log.error("[performance] Groq API call: {} failed after {}ms - Error: {}", 
                    methodName, duration, throwable.getMessage());
            
            if (meterRegistry != null) {
                meterRegistry.timer("aura.groq.api.duration",
                        "method", methodName,
                        "status", "error")
                        .record(duration, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
            
            throw throwable;
        }
    }
}