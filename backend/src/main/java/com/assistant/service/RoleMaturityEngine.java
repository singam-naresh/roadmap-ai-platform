package com.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * PHASE 5 — Role Maturity Engine
 *
 * Ensures outputs vary drastically by expertise level:
 *   BEGINNER     → foundational tools, guided steps, setup-focused
 *   INTERMEDIATE → production patterns, testing, deployment
 *   ADVANCED     → architecture, optimization, observability
 *   EXPERT       → scaling, tradeoffs, distributed systems, leadership
 *
 * Generates level-specific prompt injections and validates that
 * the generated content matches the expected maturity level.
 */
@Service
public class RoleMaturityEngine {

    private static final Logger log = LoggerFactory.getLogger(RoleMaturityEngine.class);

    // Maturity level definitions per domain
    private static final Map<String, Map<String, MaturityProfile>> MATURITY_PROFILES;
    static {
        MATURITY_PROFILES = new HashMap<String, Map<String, MaturityProfile>>();

        // AI Engineering maturity levels
        Map<String, MaturityProfile> aiProfiles = new HashMap<>();
        aiProfiles.put("BEGINNER", new MaturityProfile(
            "AI Engineering Beginner",
            List.of("Python basics", "NumPy/Pandas", "scikit-learn", "Jupyter notebooks",
                    "basic neural networks", "PyTorch fundamentals"),
            List.of("train a simple classifier", "build a text classifier", "fine-tune a small model"),
            List.of("learn", "understand", "practice", "explore", "try"),
            List.of("production", "distributed", "quantization", "RLHF", "inference optimization")
        ));
        aiProfiles.put("INTERMEDIATE", new MaturityProfile(
            "AI Engineering Intermediate",
            List.of("Hugging Face Transformers", "fine-tuning", "RAG pipelines", "MLflow",
                    "model evaluation", "data pipelines", "basic deployment"),
            List.of("fine-tune a BERT model", "build a RAG system", "deploy a model API"),
            List.of("implement", "build", "deploy", "evaluate", "optimize"),
            List.of("ZeRO-3", "FSDP", "TensorRT", "speculative decoding", "PagedAttention")
        ));
        aiProfiles.put("ADVANCED", new MaturityProfile(
            "AI Engineering Advanced",
            List.of("LoRA/QLoRA", "RLHF/DPO", "vLLM", "TensorRT-LLM", "distributed training",
                    "embedding optimization", "reranking", "model monitoring"),
            List.of("implement LoRA fine-tuning pipeline", "deploy vLLM inference server",
                    "build production RAG with reranking"),
            List.of("architect", "optimize", "instrument", "scale", "benchmark"),
            List.of("basic tutorials", "simple examples", "getting started")
        ));
        aiProfiles.put("EXPERT", new MaturityProfile(
            "AI Engineering Expert",
            List.of("DeepSpeed ZeRO-3", "FSDP", "Megatron-LM", "TensorRT-LLM", "speculative decoding",
                    "PagedAttention", "Constitutional AI", "multi-modal systems", "custom CUDA kernels"),
            List.of("design distributed training infrastructure", "implement custom attention mechanisms",
                    "architect multi-region inference platform", "build RLHF training pipeline"),
            List.of("architect", "design", "engineer", "optimize", "lead"),
            List.of("learn", "study", "understand", "basic", "simple", "tutorial")
        ));
        MATURITY_PROFILES.put("AI_ENGINEERING", aiProfiles);

        // Java Backend maturity levels
        Map<String, MaturityProfile> javaProfiles = new HashMap<>();
        javaProfiles.put("BEGINNER", new MaturityProfile(
            "Java Backend Beginner",
            List.of("Java basics", "Spring Boot", "REST APIs", "PostgreSQL", "Maven", "Git"),
            List.of("build a CRUD REST API", "connect to a database", "write unit tests"),
            List.of("create", "build", "write", "connect", "test"),
            List.of("reactive", "CQRS", "event sourcing", "distributed", "Kafka", "circuit breaker")
        ));
        javaProfiles.put("INTERMEDIATE", new MaturityProfile(
            "Java Backend Intermediate",
            List.of("Spring Security", "JPA optimization", "Docker", "Redis caching",
                    "Kafka basics", "TestContainers", "CI/CD"),
            List.of("implement JWT authentication", "add Redis caching", "containerize with Docker"),
            List.of("implement", "configure", "deploy", "optimize", "secure"),
            List.of("ZeRO", "FSDP", "saga orchestration", "event sourcing", "CQRS")
        ));
        javaProfiles.put("ADVANCED", new MaturityProfile(
            "Java Backend Advanced",
            List.of("Spring WebFlux", "CQRS", "Event Sourcing", "Kafka Streams", "Kubernetes",
                    "Istio", "distributed tracing", "circuit breakers"),
            List.of("implement CQRS with event sourcing", "deploy on Kubernetes with Istio",
                    "build reactive microservices"),
            List.of("architect", "implement", "deploy", "instrument", "optimize"),
            List.of("basic CRUD", "simple REST", "getting started", "hello world")
        ));
        javaProfiles.put("EXPERT", new MaturityProfile(
            "Java Backend Expert",
            List.of("JVM tuning", "G1GC/ZGC optimization", "distributed transactions",
                    "multi-region active-active", "chaos engineering", "capacity planning",
                    "custom Kafka connectors", "database sharding"),
            List.of("design multi-region active-active system", "implement distributed saga orchestration",
                    "architect zero-downtime migration strategy"),
            List.of("architect", "design", "engineer", "lead", "optimize"),
            List.of("learn", "basic", "simple", "tutorial", "getting started")
        ));
        MATURITY_PROFILES.put("JAVA_BACKEND", javaProfiles);

        // DevOps maturity levels
        Map<String, MaturityProfile> devopsProfiles = new HashMap<>();
        devopsProfiles.put("BEGINNER", new MaturityProfile(
            "DevOps Beginner",
            List.of("Linux basics", "Docker", "Git", "basic CI/CD", "shell scripting"),
            List.of("containerize an application", "set up a basic CI pipeline", "deploy to a VM"),
            List.of("install", "configure", "set up", "create", "run"),
            List.of("Kubernetes", "Terraform", "service mesh", "GitOps", "chaos engineering")
        ));
        devopsProfiles.put("INTERMEDIATE", new MaturityProfile(
            "DevOps Intermediate",
            List.of("Kubernetes basics", "Helm", "Terraform", "GitHub Actions", "Prometheus basics"),
            List.of("deploy on Kubernetes", "write Terraform modules", "set up monitoring"),
            List.of("deploy", "configure", "automate", "monitor", "provision"),
            List.of("Istio", "ArgoCD", "chaos engineering", "multi-cluster", "OPA")
        ));
        devopsProfiles.put("ADVANCED", new MaturityProfile(
            "DevOps Advanced",
            List.of("ArgoCD", "Istio", "OPA Gatekeeper", "Falco", "Crossplane",
                    "SLO tracking", "chaos engineering"),
            List.of("implement GitOps with ArgoCD", "deploy service mesh with Istio",
                    "implement policy as code with OPA"),
            List.of("architect", "implement", "automate", "enforce", "instrument"),
            List.of("basic Docker", "simple deployment", "getting started")
        ));
        devopsProfiles.put("EXPERT", new MaturityProfile(
            "DevOps Expert",
            List.of("multi-cluster management", "Cluster API", "custom operators",
                    "eBPF-based observability", "zero-trust networking", "cost optimization",
                    "platform engineering", "developer self-service"),
            List.of("design multi-cluster platform", "build custom Kubernetes operator",
                    "architect zero-trust network policy"),
            List.of("architect", "design", "engineer", "lead", "build"),
            List.of("learn", "basic", "simple", "tutorial", "getting started")
        ));
        MATURITY_PROFILES.put("DEVOPS", devopsProfiles);
    }

    public MaturityValidationResult validate(List<String> steps, String domain, String expertiseLevel) {
        Map<String, MaturityProfile> domainProfiles = MATURITY_PROFILES.get(domain);
        if (domainProfiles == null) {
            return new MaturityValidationResult(true, List.of(), List.of());
        }

        MaturityProfile profile = domainProfiles.get(expertiseLevel);
        if (profile == null) {
            return new MaturityValidationResult(true, List.of(), List.of());
        }

        List<String> violations = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        for (int i = 0; i < steps.size(); i++) {
            String step = steps.get(i).toLowerCase();

            // Check for forbidden terms (too advanced for level)
            for (String forbidden : profile.forbiddenTerms) {
                if (step.contains(forbidden.toLowerCase())) {
                    violations.add("Step " + (i+1) + " uses term too advanced for " + expertiseLevel
                            + ": '" + forbidden + "'");
                }
            }

            // Check for expected action verbs
            boolean hasExpectedVerb = profile.expectedActionVerbs.stream()
                    .anyMatch(v -> step.startsWith(v.toLowerCase()));
            if (!hasExpectedVerb && i < 3) { // First 3 steps must match level
                suggestions.add("Step " + (i+1) + " should start with: "
                        + String.join(", ", profile.expectedActionVerbs.subList(0, 3)));
            }
        }

        boolean valid = violations.isEmpty();
        log.debug("[role-maturity] domain={} level={} violations={}", domain, expertiseLevel, violations.size());
        return new MaturityValidationResult(valid, violations, suggestions);
    }

    public String buildMaturityPrompt(String domain, String expertiseLevel) {
        Map<String, MaturityProfile> domainProfiles = MATURITY_PROFILES.get(domain);
        if (domainProfiles == null) return "";

        MaturityProfile profile = domainProfiles.get(expertiseLevel);
        if (profile == null) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("ROLE MATURITY REQUIREMENTS (").append(expertiseLevel).append(" level):\n\n");

        sb.append("EXPECTED TECHNOLOGIES for this level:\n");
        profile.expectedTechnologies.forEach(t -> sb.append("- ").append(t).append("\n"));

        sb.append("\nEXPECTED DELIVERABLES:\n");
        profile.expectedDeliverables.forEach(d -> sb.append("- ").append(d).append("\n"));

        sb.append("\nEXPECTED ACTION VERBS: ")
          .append(String.join(", ", profile.expectedActionVerbs)).append("\n");

        if (!profile.forbiddenTerms.isEmpty()) {
            sb.append("\nFORBIDDEN for this level (too advanced/too basic):\n");
            profile.forbiddenTerms.stream().limit(5)
                    .forEach(t -> sb.append("- ").append(t).append("\n"));
        }

        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data classes
    // ─────────────────────────────────────────────────────────────────────────

    private static class MaturityProfile {
        final String       title;
        final List<String> expectedTechnologies;
        final List<String> expectedDeliverables;
        final List<String> expectedActionVerbs;
        final List<String> forbiddenTerms;

        MaturityProfile(String title, List<String> expectedTechnologies,
                        List<String> expectedDeliverables, List<String> expectedActionVerbs,
                        List<String> forbiddenTerms) {
            this.title                = title;
            this.expectedTechnologies = expectedTechnologies;
            this.expectedDeliverables = expectedDeliverables;
            this.expectedActionVerbs  = expectedActionVerbs;
            this.forbiddenTerms       = forbiddenTerms;
        }
    }

    public static class MaturityValidationResult {
        public final boolean      isValid;
        public final List<String> violations;
        public final List<String> suggestions;

        public MaturityValidationResult(boolean isValid, List<String> violations, List<String> suggestions) {
            this.isValid     = isValid;
            this.violations  = violations;
            this.suggestions = suggestions;
        }
    }
}
