package com.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * PHASE 5 — Domain-Specific Reasoning Engine
 *
 * Infers from user input:
 *   - Target career / domain
 *   - Industry expectations for that domain
 *   - Realistic skill progression order
 *   - Realistic timelines per domain
 *   - Prerequisite ordering
 *   - Ecosystem alignment (which tools belong together)
 *
 * Produces a DomainReasoning object that the pipeline injects into prompts.
 */
@Service
public class DomainReasoningEngine {

    private static final Logger log = LoggerFactory.getLogger(DomainReasoningEngine.class);

    // Domain reasoning profiles
    private static final Map<String, DomainProfile> PROFILES;
    static {
        PROFILES = new HashMap<>();

        PROFILES.put("AI_ENGINEERING", new DomainProfile(
            "AI Engineering",
            "Build, train, fine-tune, and serve large-scale ML/AI systems in production",
            List.of(
                "Python fundamentals + NumPy/Pandas",
                "PyTorch fundamentals + autograd",
                "Transformer architecture + attention mechanisms",
                "Hugging Face Transformers + datasets",
                "Fine-tuning with LoRA/QLoRA + PEFT",
                "RLHF/DPO alignment techniques",
                "Inference optimization: vLLM, TensorRT-LLM",
                "RAG pipeline: chunking, embedding, retrieval, reranking",
                "Production serving: Triton, Ray Serve, BentoML",
                "Distributed training: DeepSpeed ZeRO, FSDP",
                "MLOps: MLflow, Weights & Biases, DVC",
                "Monitoring: embedding drift, latency, throughput"
            ),
            List.of("PyTorch", "Hugging Face", "vLLM", "DeepSpeed", "FAISS", "Weaviate", "MLflow"),
            Map.of(
                "BEGINNER",     "6–12 months",
                "INTERMEDIATE", "3–6 months",
                "ADVANCED",     "2–4 months",
                "EXPERT",       "4–8 weeks"
            ),
            "Production AI systems require GPU infrastructure, distributed training expertise, and deep understanding of model internals."
        ));

        PROFILES.put("JAVA_BACKEND", new DomainProfile(
            "Java Backend Engineering",
            "Build enterprise-grade, high-throughput backend systems with Spring Boot and cloud-native patterns",
            List.of(
                "Java 17+ features: records, sealed classes, pattern matching",
                "Spring Boot 3.x: auto-configuration, actuator, security",
                "Spring WebFlux: reactive programming, Project Reactor",
                "JPA/Hibernate: entity mapping, query optimization, N+1 prevention",
                "PostgreSQL: indexing, partitioning, connection pooling with PgBouncer",
                "Apache Kafka: producers, consumers, Kafka Streams, Schema Registry",
                "Redis Cluster: caching strategies, pub/sub, distributed locks",
                "Docker + Kubernetes: containerization, Helm charts, health probes",
                "Observability: Micrometer, Prometheus, Grafana, Jaeger",
                "Security: JWT, OAuth2, Spring Security 6, RBAC",
                "Testing: JUnit 5, Mockito, TestContainers, WireMock",
                "Architecture: CQRS, Event Sourcing, Saga Pattern, Circuit Breaker"
            ),
            List.of("Spring Boot", "PostgreSQL", "Kafka", "Redis", "Docker", "Kubernetes", "Prometheus"),
            Map.of(
                "BEGINNER",     "8–12 months",
                "INTERMEDIATE", "4–6 months",
                "ADVANCED",     "2–3 months",
                "EXPERT",       "4–6 weeks"
            ),
            "Enterprise Java requires deep JVM knowledge, distributed systems expertise, and production-grade observability."
        ));

        PROFILES.put("REACT_FRONTEND", new DomainProfile(
            "React Frontend Engineering",
            "Build high-performance, production-grade React applications with modern tooling",
            List.of(
                "React 18: concurrent features, automatic batching, transitions",
                "TypeScript: strict mode, generics, utility types, discriminated unions",
                "Next.js 14: App Router, Server Components, streaming SSR",
                "State management: React Query/TanStack Query, Zustand, Jotai",
                "Styling: Tailwind CSS, CSS Modules, Radix UI primitives",
                "Build tooling: Vite, SWC, Turbopack, bundle analysis",
                "Testing: Vitest, React Testing Library, Playwright E2E",
                "Performance: Core Web Vitals, code splitting, lazy loading, virtualization",
                "Accessibility: ARIA, keyboard navigation, screen reader testing",
                "Deployment: Vercel, Cloudflare Pages, CDN optimization",
                "Monitoring: Sentry, LogRocket, Real User Monitoring",
                "Architecture: Micro-frontends, Module Federation, Islands Architecture"
            ),
            List.of("React 18", "Next.js", "TypeScript", "Tailwind CSS", "Vite", "Vitest", "Playwright"),
            Map.of(
                "BEGINNER",     "6–10 months",
                "INTERMEDIATE", "3–5 months",
                "ADVANCED",     "2–3 months",
                "EXPERT",       "3–5 weeks"
            ),
            "Modern frontend requires deep React internals knowledge, performance optimization expertise, and accessibility awareness."
        ));

        PROFILES.put("DEVOPS", new DomainProfile(
            "DevOps / Platform Engineering",
            "Build and operate production infrastructure with GitOps, observability, and SRE practices",
            List.of(
                "Linux fundamentals: networking, systemd, cgroups, namespaces",
                "Docker: multi-stage builds, layer caching, security hardening",
                "Kubernetes: workloads, services, RBAC, network policies, storage",
                "Helm: chart development, templating, lifecycle hooks",
                "ArgoCD: GitOps workflows, app-of-apps, sync waves",
                "Terraform: modules, state management, workspaces, drift detection",
                "Prometheus + Grafana: metrics, alerting, dashboards, SLO tracking",
                "Istio: traffic management, mTLS, circuit breaking, observability",
                "CI/CD: GitHub Actions, GitLab CI, pipeline optimization",
                "Security: OPA Gatekeeper, Falco, Vault, cert-manager",
                "SRE: error budgets, SLI/SLO/SLA, toil reduction, chaos engineering",
                "Multi-cluster: Crossplane, Cluster API, federation"
            ),
            List.of("Kubernetes", "Terraform", "ArgoCD", "Prometheus", "Istio", "Helm", "Vault"),
            Map.of(
                "BEGINNER",     "10–14 months",
                "INTERMEDIATE", "4–6 months",
                "ADVANCED",     "2–4 months",
                "EXPERT",       "4–8 weeks"
            ),
            "Platform engineering requires deep infrastructure knowledge, security expertise, and production incident experience."
        ));

        PROFILES.put("DATA_ENGINEERING", new DomainProfile(
            "Data Engineering",
            "Build reliable, scalable data pipelines and analytics infrastructure",
            List.of(
                "SQL mastery: window functions, CTEs, query optimization",
                "Python: Pandas, PySpark, data manipulation at scale",
                "Apache Spark: RDDs, DataFrames, Spark SQL, streaming",
                "Apache Kafka: event streaming, exactly-once semantics",
                "Apache Airflow: DAG design, operators, sensors, XComs",
                "dbt: models, tests, documentation, incremental strategies",
                "Data warehousing: Snowflake, BigQuery, Redshift, columnar storage",
                "Data lake: Delta Lake, Apache Iceberg, Hudi, ACID transactions",
                "Streaming: Apache Flink, Kafka Streams, real-time aggregations",
                "Data quality: Great Expectations, Monte Carlo, data contracts",
                "Orchestration: Prefect, Dagster, Mage",
                "Infrastructure: Docker, Kubernetes, Terraform for data platforms"
            ),
            List.of("Apache Spark", "Kafka", "Airflow", "dbt", "Snowflake", "Delta Lake", "Flink"),
            Map.of(
                "BEGINNER",     "8–12 months",
                "INTERMEDIATE", "4–6 months",
                "ADVANCED",     "2–3 months",
                "EXPERT",       "4–6 weeks"
            ),
            "Data engineering requires SQL mastery, distributed systems knowledge, and data quality engineering expertise."
        ));

        PROFILES.put("SYSTEM_DESIGN", new DomainProfile(
            "System Design / Distributed Systems",
            "Design and implement large-scale distributed systems with high availability and performance",
            List.of(
                "Distributed systems fundamentals: CAP theorem, consistency models",
                "Load balancing: L4/L7, consistent hashing, least connections",
                "Caching: Redis Cluster, Memcached, cache invalidation strategies",
                "Message queues: Kafka, SQS, RabbitMQ, at-least-once vs exactly-once",
                "Database design: sharding, partitioning, replication, CQRS",
                "API design: REST, gRPC, GraphQL, rate limiting, versioning",
                "Consensus: Raft, Paxos, leader election, distributed locks",
                "Observability: distributed tracing, metrics, structured logging",
                "Reliability: circuit breakers, bulkheads, retry with backoff",
                "Security: zero-trust, mTLS, API gateway, DDoS mitigation",
                "Performance: profiling, benchmarking, capacity planning",
                "Deployment: blue-green, canary, feature flags, rollback strategies"
            ),
            List.of("Kafka", "Redis", "PostgreSQL", "Kubernetes", "Prometheus", "Envoy", "Consul"),
            Map.of(
                "BEGINNER",     "12–18 months",
                "INTERMEDIATE", "6–9 months",
                "ADVANCED",     "3–5 months",
                "EXPERT",       "6–10 weeks"
            ),
            "System design mastery requires hands-on experience with production failures, scaling challenges, and trade-off analysis."
        ));
    }

    public DomainReasoning reason(String userInput, String detectedDomain, String expertiseLevel) {
        DomainProfile profile = PROFILES.getOrDefault(detectedDomain, getGeneralProfile());

        String timeline = profile.typicalTimelines.getOrDefault(expertiseLevel, "3–6 months");
        List<String> progression = adjustProgressionForLevel(profile.skillProgression, expertiseLevel);

        DomainReasoning reasoning = new DomainReasoning(
                detectedDomain,
                profile.title,
                profile.industryExpectation,
                progression,
                profile.coreEcosystem,
                timeline,
                profile.expertNote,
                expertiseLevel
        );

        log.debug("[domain-reasoning] domain={} level={} timeline={} steps={}",
                detectedDomain, expertiseLevel, timeline, progression.size());
        return reasoning;
    }

    public String buildReasoningPrompt(DomainReasoning reasoning) {
        StringBuilder sb = new StringBuilder();
        sb.append("DOMAIN REASONING CONTEXT:\n");
        sb.append("Domain: ").append(reasoning.domainTitle).append("\n");
        sb.append("Industry Expectation: ").append(reasoning.industryExpectation).append("\n");
        sb.append("Realistic Timeline: ").append(reasoning.realisticTimeline).append("\n");
        sb.append("Expertise Level: ").append(reasoning.expertiseLevel).append("\n\n");

        sb.append("CANONICAL SKILL PROGRESSION (follow this order):\n");
        for (int i = 0; i < reasoning.skillProgression.size(); i++) {
            sb.append(i + 1).append(". ").append(reasoning.skillProgression.get(i)).append("\n");
        }

        sb.append("\nCORE ECOSYSTEM (use these technologies):\n");
        reasoning.coreEcosystem.forEach(t -> sb.append("- ").append(t).append("\n"));

        sb.append("\nEXPERT NOTE: ").append(reasoning.expertNote).append("\n");
        return sb.toString();
    }

    private List<String> adjustProgressionForLevel(List<String> full, String level) {
        return switch (level) {
            case "EXPERT"       -> full; // all steps
            case "ADVANCED"     -> full.subList(Math.min(2, full.size()), full.size()); // skip basics
            case "INTERMEDIATE" -> full.subList(0, Math.min(8, full.size()));
            default             -> full.subList(0, Math.min(5, full.size())); // beginner: first 5
        };
    }

    private DomainProfile getGeneralProfile() {
        return new DomainProfile(
            "Software Engineering",
            "Build production-quality software with modern tools and practices",
            List.of("Core language fundamentals", "Version control with Git", "Testing and TDD",
                    "CI/CD pipelines", "Docker containerization", "Cloud deployment", "Observability"),
            List.of("Git", "Docker", "PostgreSQL", "REST APIs", "CI/CD"),
            Map.of("BEGINNER", "6–12 months", "INTERMEDIATE", "3–6 months",
                   "ADVANCED", "2–3 months", "EXPERT", "4–6 weeks"),
            "Production software requires testing discipline, observability, and deployment automation."
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data classes
    // ─────────────────────────────────────────────────────────────────────────

    private static class DomainProfile {
        final String              title;
        final String              industryExpectation;
        final List<String>        skillProgression;
        final List<String>        coreEcosystem;
        final Map<String, String> typicalTimelines;
        final String              expertNote;

        DomainProfile(String title, String industryExpectation, List<String> skillProgression,
                      List<String> coreEcosystem, Map<String, String> typicalTimelines, String expertNote) {
            this.title               = title;
            this.industryExpectation = industryExpectation;
            this.skillProgression    = skillProgression;
            this.coreEcosystem       = coreEcosystem;
            this.typicalTimelines    = typicalTimelines;
            this.expertNote          = expertNote;
        }
    }

    public static class DomainReasoning {
        public final String       domain;
        public final String       domainTitle;
        public final String       industryExpectation;
        public final List<String> skillProgression;
        public final List<String> coreEcosystem;
        public final String       realisticTimeline;
        public final String       expertNote;
        public final String       expertiseLevel;

        public DomainReasoning(String domain, String domainTitle, String industryExpectation,
                               List<String> skillProgression, List<String> coreEcosystem,
                               String realisticTimeline, String expertNote, String expertiseLevel) {
            this.domain              = domain;
            this.domainTitle         = domainTitle;
            this.industryExpectation = industryExpectation;
            this.skillProgression    = skillProgression;
            this.coreEcosystem       = coreEcosystem;
            this.realisticTimeline   = realisticTimeline;
            this.expertNote          = expertNote;
            this.expertiseLevel      = expertiseLevel;
        }
    }
}
