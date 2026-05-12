package com.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * PHASE 5 — Roadmap Specificity Engine
 *
 * Converts vague steps into implementation-focused, technology-specific steps.
 *
 * Every output step must contain:
 *   1. An action verb (build, implement, deploy, configure…)
 *   2. A named technology (Spring Boot, Kubernetes, PyTorch…)
 *   3. A measurable goal (with JWT auth, targeting <50ms p99…)
 *   4. An implementation target (REST API, Helm chart, fine-tuned model…)
 *
 * BAD:  "Learn backend development"
 * GOOD: "Build a Spring Boot 3.x REST API with JWT authentication, PostgreSQL
 *        persistence via JPA, Docker containerization, and JUnit 5 integration tests"
 */
@Service
public class RoadmapSpecificityEngine {

    private static final Logger log = LoggerFactory.getLogger(RoadmapSpecificityEngine.class);

    // Vague step patterns that need enhancement
    private static final Map<Pattern, String> VAGUE_TO_SPECIFIC = new LinkedHashMap<>();
    static {
        VAGUE_TO_SPECIFIC.put(
            Pattern.compile("(?i)^learn (backend|server.?side)"),
            "Build a Spring Boot 3.x REST API with JWT authentication, PostgreSQL persistence, Docker containerization, and JUnit 5 integration tests"
        );
        VAGUE_TO_SPECIFIC.put(
            Pattern.compile("(?i)^learn (frontend|ui|react)"),
            "Build a React 18 application with TypeScript, React Query for data fetching, Tailwind CSS styling, and Vitest unit tests"
        );
        VAGUE_TO_SPECIFIC.put(
            Pattern.compile("(?i)^learn (machine learning|ml|ai)"),
            "Implement a PyTorch training pipeline with Hugging Face Transformers, LoRA fine-tuning, and MLflow experiment tracking"
        );
        VAGUE_TO_SPECIFIC.put(
            Pattern.compile("(?i)^(learn|study|understand) (kubernetes|k8s)"),
            "Deploy a multi-service application on Kubernetes with Helm charts, Horizontal Pod Autoscaling, Prometheus monitoring, and ArgoCD GitOps"
        );
        VAGUE_TO_SPECIFIC.put(
            Pattern.compile("(?i)^(learn|study|understand) (docker|containers?)"),
            "Containerize a multi-service application with Docker multi-stage builds, Docker Compose for local development, and security hardening with non-root users"
        );
        VAGUE_TO_SPECIFIC.put(
            Pattern.compile("(?i)^(set up|setup|configure) (database|db)"),
            "Configure PostgreSQL with connection pooling via PgBouncer, read replicas, automated backups, and query performance monitoring with pg_stat_statements"
        );
        VAGUE_TO_SPECIFIC.put(
            Pattern.compile("(?i)^(add|implement|set up) (monitoring|observability)"),
            "Instrument services with Micrometer, deploy Prometheus + Grafana stack, configure distributed tracing with Jaeger/OpenTelemetry, and set up AlertManager rules"
        );
        VAGUE_TO_SPECIFIC.put(
            Pattern.compile("(?i)^(add|implement|set up) (security|auth)"),
            "Implement OAuth2/OIDC with Spring Security 6, JWT token rotation, RBAC authorization, rate limiting, and OWASP security headers"
        );
        VAGUE_TO_SPECIFIC.put(
            Pattern.compile("(?i)^(add|implement|set up) (ci.?cd|pipeline|deployment)"),
            "Build a GitHub Actions CI/CD pipeline with automated testing, Docker image building, vulnerability scanning with Trivy, and ArgoCD GitOps deployment"
        );
        VAGUE_TO_SPECIFIC.put(
            Pattern.compile("(?i)^(add|implement|set up) (caching|cache)"),
            "Implement Redis Cluster caching with write-through strategy, TTL management, cache invalidation patterns, and cache hit rate monitoring"
        );
    }

    // Specificity requirements per domain
    private static final Map<String, SpecificityRequirements> DOMAIN_REQUIREMENTS;
    static {
        DOMAIN_REQUIREMENTS = new HashMap<>();
        DOMAIN_REQUIREMENTS.put("AI_ENGINEERING", new SpecificityRequirements(
            List.of("PyTorch", "Hugging Face", "vLLM", "DeepSpeed", "FAISS", "MLflow"),
            List.of("training pipeline", "inference server", "fine-tuning", "RAG pipeline", "embedding service"),
            List.of("throughput", "latency", "GPU utilization", "BLEU score", "perplexity", "accuracy")
        ));
        DOMAIN_REQUIREMENTS.put("JAVA_BACKEND", new SpecificityRequirements(
            List.of("Spring Boot", "PostgreSQL", "Kafka", "Redis", "Docker", "Kubernetes"),
            List.of("REST API", "microservice", "event-driven service", "CQRS implementation", "saga orchestrator"),
            List.of("p99 latency", "throughput", "connection pool size", "cache hit rate", "error rate")
        ));
        DOMAIN_REQUIREMENTS.put("REACT_FRONTEND", new SpecificityRequirements(
            List.of("React 18", "Next.js", "TypeScript", "Tailwind CSS", "Vite"),
            List.of("component library", "dashboard", "SSR application", "PWA", "micro-frontend"),
            List.of("LCP", "FID", "CLS", "bundle size", "Time to Interactive", "Lighthouse score")
        ));
        DOMAIN_REQUIREMENTS.put("DEVOPS", new SpecificityRequirements(
            List.of("Kubernetes", "Terraform", "ArgoCD", "Prometheus", "Istio"),
            List.of("GitOps pipeline", "observability stack", "service mesh", "IaC module", "operator"),
            List.of("deployment frequency", "MTTR", "change failure rate", "availability SLO", "error budget")
        ));
    }

    public SpecificityAnalysisResult analyzeStep(String step, String domain) {
        SpecificityRequirements reqs = DOMAIN_REQUIREMENTS.getOrDefault(domain, getDefaultRequirements());

        boolean hasAction      = hasActionVerb(step);
        boolean hasTechnology  = hasTechnology(step, reqs);
        boolean hasGoal        = hasMeasurableGoal(step, reqs);
        boolean hasTarget      = hasImplementationTarget(step, reqs);

        double score = 0.0;
        if (hasAction)     score += 0.25;
        if (hasTechnology) score += 0.35;
        if (hasGoal)       score += 0.20;
        if (hasTarget)     score += 0.20;

        List<String> missing = new ArrayList<>();
        if (!hasAction)     missing.add("action verb");
        if (!hasTechnology) missing.add("named technology");
        if (!hasGoal)       missing.add("measurable goal");
        if (!hasTarget)     missing.add("implementation target");

        return new SpecificityAnalysisResult(score, hasAction, hasTechnology, hasGoal, hasTarget, missing);
    }

    public String buildSpecificityPrompt(String domain) {
        SpecificityRequirements reqs = DOMAIN_REQUIREMENTS.getOrDefault(domain, getDefaultRequirements());
        return """
            STEP SPECIFICITY REQUIREMENTS (PHASE 5):
            
            Every roadmap step MUST contain ALL FOUR elements:
            1. ACTION VERB: build, implement, deploy, configure, architect, instrument, optimize...
            2. NAMED TECHNOLOGY: """ + String.join(", ", reqs.coreTechnologies.subList(0, Math.min(4, reqs.coreTechnologies.size()))) + """
            3. IMPLEMENTATION TARGET: """ + String.join(", ", reqs.implementationTargets.subList(0, Math.min(3, reqs.implementationTargets.size()))) + """
            4. MEASURABLE GOAL: """ + String.join(", ", reqs.measurableGoals.subList(0, Math.min(3, reqs.measurableGoals.size()))) + """
            
            BAD EXAMPLE:  "Learn backend development"
            GOOD EXAMPLE: "Build a Spring Boot 3.x REST API with JWT authentication, PostgreSQL persistence via JPA, Docker containerization, and JUnit 5 integration tests targeting 95%+ code coverage"
            
            BAD EXAMPLE:  "Set up monitoring"
            GOOD EXAMPLE: "Instrument services with Micrometer metrics, deploy Prometheus + Grafana stack with custom dashboards, configure Jaeger distributed tracing, and set up AlertManager rules for p99 latency > 200ms"
            
            REJECT any step that is vague, generic, or missing concrete technology names.
            """;
    }

    private boolean hasActionVerb(String step) {
        return Pattern.compile("(?i)^(build|implement|deploy|configure|architect|design|create|develop|" +
                "integrate|optimize|provision|orchestrate|automate|containerize|instrument|benchmark|" +
                "profile|migrate|refactor|secure|harden|monitor|observe|test|validate|ship|scale|tune|" +
                "fine-tune|quantize|train|serve|expose|publish|stream|process|transform|aggregate)\\b")
                .matcher(step.trim()).find();
    }

    private boolean hasTechnology(String step, SpecificityRequirements reqs) {
        String lower = step.toLowerCase();
        return reqs.coreTechnologies.stream().anyMatch(t -> lower.contains(t.toLowerCase()));
    }

    private boolean hasMeasurableGoal(String step, SpecificityRequirements reqs) {
        String lower = step.toLowerCase();
        // Check for metrics or measurable outcomes
        return reqs.measurableGoals.stream().anyMatch(g -> lower.contains(g.toLowerCase()))
                || lower.contains("targeting")
                || lower.contains("achieving")
                || lower.contains("ensuring")
                || lower.contains("with <")
                || lower.contains("p99")
                || lower.contains("slo")
                || lower.contains("coverage")
                || lower.contains("throughput");
    }

    private boolean hasImplementationTarget(String step, SpecificityRequirements reqs) {
        String lower = step.toLowerCase();
        return reqs.implementationTargets.stream().anyMatch(t -> lower.contains(t.toLowerCase()))
                || lower.contains("api")
                || lower.contains("service")
                || lower.contains("pipeline")
                || lower.contains("cluster")
                || lower.contains("chart")
                || lower.contains("module");
    }

    private SpecificityRequirements getDefaultRequirements() {
        return new SpecificityRequirements(
            List.of("Docker", "PostgreSQL", "REST API", "Git", "CI/CD"),
            List.of("service", "API", "pipeline", "application", "module"),
            List.of("test coverage", "latency", "throughput", "availability")
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data classes
    // ─────────────────────────────────────────────────────────────────────────

    public static class SpecificityRequirements {
        public final List<String> coreTechnologies;
        public final List<String> implementationTargets;
        public final List<String> measurableGoals;

        public SpecificityRequirements(List<String> coreTechnologies,
                                       List<String> implementationTargets,
                                       List<String> measurableGoals) {
            this.coreTechnologies     = coreTechnologies;
            this.implementationTargets = implementationTargets;
            this.measurableGoals      = measurableGoals;
        }
    }

    public static class SpecificityAnalysisResult {
        public final double       score;
        public final boolean      hasActionVerb;
        public final boolean      hasTechnology;
        public final boolean      hasMeasurableGoal;
        public final boolean      hasImplementationTarget;
        public final List<String> missingElements;

        public SpecificityAnalysisResult(double score, boolean hasActionVerb, boolean hasTechnology,
                                         boolean hasMeasurableGoal, boolean hasImplementationTarget,
                                         List<String> missingElements) {
            this.score                  = score;
            this.hasActionVerb          = hasActionVerb;
            this.hasTechnology          = hasTechnology;
            this.hasMeasurableGoal      = hasMeasurableGoal;
            this.hasImplementationTarget = hasImplementationTarget;
            this.missingElements        = missingElements;
        }
    }
}
