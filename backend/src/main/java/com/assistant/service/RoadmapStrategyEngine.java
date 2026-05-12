package com.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Roadmap Strategy Engine — generates level-appropriate, domain-specific
 * roadmap strategies and injects them as hard constraints into the AI prompt.
 *
 * The strategy prompt is the FIRST system message and overrides everything else.
 * It enforces:
 *   - Exact step count
 *   - Difficulty label
 *   - Timeline
 *   - Forbidden topics (hard block for beginners)
 *   - Required topics (must appear)
 *   - Step-by-step sequence for absolute beginners
 */
@Service
public class RoadmapStrategyEngine {

    private static final Logger log = LoggerFactory.getLogger(RoadmapStrategyEngine.class);

    public record RoadmapStrategy(
        String difficultyLabel,
        String realisticTimeline,
        String contextualIntro,
        String stepStrategy,
        int    recommendedStepCount,
        List<String> forbiddenTopics,
        List<String> requiredTopics
    ) {}

    public RoadmapStrategy buildStrategy(SkillInferenceService.SkillLevel level,
                                          String domain, String userInput) {
        return switch (level) {
            case ABSOLUTE_BEGINNER -> buildAbsoluteBeginnerStrategy(domain, userInput);
            case BEGINNER          -> buildBeginnerStrategy(domain, userInput);
            case INTERMEDIATE      -> buildIntermediateStrategy(domain, userInput);
            case ADVANCED          -> buildAdvancedStrategy(domain, userInput);
            case EXPERT            -> buildExpertStrategy(domain, userInput);
        };
    }

    // =========================================================================
    // Strategy builders
    // =========================================================================

    private RoadmapStrategy buildAbsoluteBeginnerStrategy(String domain, String userInput) {
        String domainDisplay = domainDisplay(domain);
        String intro = "You're starting from zero — this roadmap covers " + domainDisplay +
                " fundamentals step by step. No prior experience assumed. " +
                "Every step builds directly on the previous one.";

        return new RoadmapStrategy(
            "Beginner",
            "4–6 months",
            intro,
            buildAbsoluteBeginnerStepStrategy(domain),
            10,
            getAbsoluteBeginnerForbiddenTopics(domain),
            getAbsoluteBeginnerRequiredTopics(domain)
        );
    }

    private RoadmapStrategy buildBeginnerStrategy(String domain, String userInput) {
        String domainDisplay = domainDisplay(domain);
        String intro = "This roadmap starts with " + domainDisplay + " essentials and builds " +
                "toward practical projects. Each step is achievable in a few days of focused practice.";

        return new RoadmapStrategy(
            "Beginner",
            "3–5 months",
            intro,
            "Start with core language/framework fundamentals, then build simple projects, " +
            "then introduce basic tooling. Avoid advanced patterns until fundamentals are solid.",
            8,
            getBeginnerForbiddenTopics(domain),
            getBeginnerRequiredTopics(domain)
        );
    }

    private RoadmapStrategy buildIntermediateStrategy(String domain, String userInput) {
        String domainDisplay = domainDisplay(domain);
        String intro = "This roadmap assumes you know " + domainDisplay + " fundamentals and focuses " +
                "on building production-ready applications with proper architecture, testing, and deployment.";

        return new RoadmapStrategy(
            "Intermediate",
            "2–4 months",
            intro,
            "Focus on production patterns, testing, deployment, and real-world tooling. " +
            "Each step should produce a deployable, tested component.",
            8,
            List.of(),
            getIntermediateRequiredTopics(domain)
        );
    }

    private RoadmapStrategy buildAdvancedStrategy(String domain, String userInput) {
        String domainDisplay = domainDisplay(domain);
        String intro = "This roadmap assumes solid " + domainDisplay + " experience and focuses on " +
                "production-scale architecture, performance optimization, and advanced patterns.";

        return new RoadmapStrategy(
            "Advanced",
            "2–3 months",
            intro,
            "Focus on architecture decisions, performance tuning, observability, and production deployment. " +
            "Every step should address real production concerns.",
            7,
            List.of(),
            getAdvancedRequiredTopics(domain)
        );
    }

    private RoadmapStrategy buildExpertStrategy(String domain, String userInput) {
        String domainDisplay = domainDisplay(domain);
        String intro = "This roadmap assumes deep " + domainDisplay + " expertise and focuses on " +
                "production-scale infrastructure, distributed systems, and operational excellence at scale.";

        return new RoadmapStrategy(
            "Advanced",
            "3–6 months",
            intro,
            "Focus on cutting-edge tooling, distributed systems design, performance at scale, " +
            "and production incident management.",
            10,
            List.of(),
            getExpertRequiredTopics(domain)
        );
    }

    // =========================================================================
    // Prompt builder — this is the FIRST system message, highest priority
    // =========================================================================

    public String buildStrategyPrompt(RoadmapStrategy strategy,
                                       SkillInferenceService.SkillLevel level,
                                       String domain) {
        StringBuilder sb = new StringBuilder();

        sb.append("SKILL LEVEL: ").append(level.name()).append("\n");
        sb.append("DIFFICULTY: ").append(strategy.difficultyLabel()).append("\n");
        sb.append("TIMELINE: ").append(strategy.realisticTimeline()).append("\n");
        sb.append("STEPS: Exactly ").append(strategy.recommendedStepCount()).append("\n\n");

        sb.append("SUMMARY (use verbatim): ").append(strategy.contextualIntro()).append("\n\n");

        if (!strategy.forbiddenTopics().isEmpty()) {
            sb.append("FORBIDDEN TOPICS (do NOT include):\n");
            strategy.forbiddenTopics().stream().limit(10)
                    .forEach(t -> sb.append("  ✗ ").append(t).append("\n"));
            sb.append("\n");
        }

        if (!strategy.requiredTopics().isEmpty()) {
            sb.append("REQUIRED TOPICS (must appear):\n");
            strategy.requiredTopics().stream().limit(8)
                    .forEach(t -> sb.append("  ✓ ").append(t).append("\n"));
            sb.append("\n");
        }

        // Only include the step sequence for absolute beginners — it's the most critical
        if (level == SkillInferenceService.SkillLevel.ABSOLUTE_BEGINNER) {
            sb.append("STEP SEQUENCE:\n");
            sb.append(strategy.stepStrategy());
        }

        return sb.toString();
    }

    // =========================================================================
    // Topic lists
    // =========================================================================

    private List<String> getAbsoluteBeginnerForbiddenTopics(String domain) {
        List<String> common = new ArrayList<>(List.of(
            "Kubernetes", "Docker Compose advanced", "Terraform", "CI/CD pipelines",
            "microservices", "distributed systems", "message queues", "Kafka",
            "Redis", "cloud architecture", "load balancing", "service mesh",
            "JWT authentication", "OAuth", "API Gateway", "gRPC",
            "event sourcing", "CQRS", "saga pattern", "circuit breaker"
        ));
        switch (domain) {
            case "JAVA_BACKEND" -> common.addAll(List.of(
                "Spring Boot", "Spring Security", "JPA", "Hibernate",
                "Maven advanced", "Gradle", "REST API design", "WebFlux"
            ));
            case "REACT_FRONTEND" -> common.addAll(List.of(
                "Next.js", "TypeScript", "Redux", "GraphQL", "SSR",
                "Server Components", "Webpack config", "Vite advanced"
            ));
            case "AI_ENGINEERING" -> common.addAll(List.of(
                "DeepSpeed", "vLLM", "TensorRT", "RLHF", "LoRA", "QLoRA",
                "distributed training", "FSDP", "model serving", "RAG"
            ));
            case "DEVOPS" -> common.addAll(List.of(
                "Kubernetes", "Helm", "Istio", "ArgoCD", "Terraform",
                "multi-cluster", "GitOps", "chaos engineering", "eBPF"
            ));
        }
        return common;
    }

    private List<String> getAbsoluteBeginnerRequiredTopics(String domain) {
        return switch (domain) {
            case "JAVA_BACKEND" -> List.of(
                "Install Java JDK 21 and IntelliJ IDEA",
                "Variables, data types, and operators",
                "Control flow: if/else, for loops, while loops",
                "Methods: parameters, return types, overloading",
                "Arrays and ArrayList",
                "Object-Oriented Programming: classes, objects, constructors",
                "Inheritance, interfaces, polymorphism",
                "Exception handling: try/catch/finally",
                "Collections: HashMap, HashSet",
                "Build a simple console application"
            );
            case "REACT_FRONTEND" -> List.of(
                "HTML: structure, semantic elements, forms",
                "CSS: selectors, box model, flexbox, grid",
                "JavaScript: variables, functions, DOM manipulation",
                "ES6+: arrow functions, destructuring, promises",
                "npm basics and package management",
                "React: components, JSX, props",
                "React: useState and event handling",
                "React: useEffect and data fetching",
                "React: forms and controlled components",
                "Build a simple React app (todo list)"
            );
            case "AI_ENGINEERING" -> List.of(
                "Python fundamentals: variables, loops, functions",
                "NumPy: arrays, operations, broadcasting",
                "Pandas: DataFrames, data loading, analysis",
                "Matplotlib: plotting and visualization",
                "Statistics: mean, variance, distributions",
                "scikit-learn: train/test split, linear regression",
                "Neural network concepts: neurons, layers, activation",
                "PyTorch basics: tensors, autograd",
                "Training loop: forward pass, loss, backprop",
                "Build a simple image or text classifier"
            );
            case "DEVOPS" -> List.of(
                "Linux command line basics",
                "Shell scripting fundamentals",
                "Git: commits, branches, merges",
                "Docker basics: images, containers, Dockerfile",
                "Basic networking: IP, DNS, HTTP",
                "Simple CI pipeline with GitHub Actions",
                "Deploy a simple app to a cloud VM",
                "Basic monitoring with logs"
            );
            default -> List.of(
                "Programming fundamentals",
                "Core language syntax",
                "Basic data structures",
                "Simple algorithms",
                "Build a small project"
            );
        };
    }

    private List<String> getBeginnerForbiddenTopics(String domain) {
        return switch (domain) {
            case "JAVA_BACKEND" -> List.of(
                "Kafka", "Kubernetes", "microservices", "distributed systems",
                "CQRS", "event sourcing", "WebFlux", "reactive programming"
            );
            case "REACT_FRONTEND" -> List.of(
                "Next.js SSR", "Server Components", "Module Federation",
                "micro-frontends", "WebAssembly"
            );
            case "AI_ENGINEERING" -> List.of(
                "DeepSpeed", "vLLM", "RLHF", "distributed training",
                "TensorRT", "model parallelism"
            );
            default -> List.of("distributed", "production-scale", "enterprise architecture");
        };
    }

    private List<String> getBeginnerRequiredTopics(String domain) {
        return switch (domain) {
            case "JAVA_BACKEND" -> List.of(
                "Spring Boot basics", "REST API with Spring MVC",
                "PostgreSQL with Spring Data JPA", "JUnit testing", "Docker basics"
            );
            case "REACT_FRONTEND" -> List.of(
                "React hooks", "component composition", "API calls with fetch/axios",
                "React Router basics", "basic state management"
            );
            case "AI_ENGINEERING" -> List.of(
                "PyTorch", "Hugging Face Transformers basics",
                "model training", "evaluation metrics", "model deployment with FastAPI"
            );
            default -> List.of("core concepts", "basic tooling", "simple project");
        };
    }

    private List<String> getIntermediateRequiredTopics(String domain) {
        return switch (domain) {
            case "JAVA_BACKEND" -> List.of(
                "Spring Security with JWT", "JPA/Hibernate advanced",
                "Docker and Docker Compose", "integration testing", "CI/CD basics"
            );
            case "REACT_FRONTEND" -> List.of(
                "TypeScript with React", "Redux Toolkit or Zustand",
                "React Testing Library", "performance optimization", "Next.js basics"
            );
            case "AI_ENGINEERING" -> List.of(
                "fine-tuning with LoRA/QLoRA", "RAG pipeline",
                "model serving with FastAPI", "MLflow experiment tracking"
            );
            case "DEVOPS" -> List.of(
                "Kubernetes basics", "Helm charts", "Terraform",
                "Prometheus and Grafana", "CI/CD with GitHub Actions"
            );
            default -> List.of("production patterns", "testing", "deployment", "monitoring");
        };
    }

    private List<String> getAdvancedRequiredTopics(String domain) {
        return switch (domain) {
            case "JAVA_BACKEND" -> List.of(
                "Apache Kafka event streaming", "distributed tracing with Jaeger",
                "JVM performance tuning", "Kubernetes deployment", "observability"
            );
            case "REACT_FRONTEND" -> List.of(
                "React Server Components", "streaming SSR",
                "bundle optimization", "Core Web Vitals", "micro-frontends"
            );
            case "AI_ENGINEERING" -> List.of(
                "LoRA/QLoRA fine-tuning", "vLLM inference server",
                "distributed training with DeepSpeed", "model monitoring"
            );
            case "DEVOPS" -> List.of(
                "ArgoCD GitOps", "Istio service mesh",
                "chaos engineering", "SLO/SLI tracking", "multi-cluster Kubernetes"
            );
            default -> List.of("architecture", "scalability", "observability", "performance");
        };
    }

    private List<String> getExpertRequiredTopics(String domain) {
        return switch (domain) {
            case "JAVA_BACKEND" -> List.of(
                "JVM tuning and GC optimization", "distributed transactions",
                "multi-region deployment", "chaos engineering", "custom JVM agents"
            );
            case "AI_ENGINEERING" -> List.of(
                "DeepSpeed ZeRO-3", "TensorRT-LLM optimization",
                "RLHF pipeline", "production inference platform", "model distillation"
            );
            case "DEVOPS" -> List.of(
                "platform engineering", "custom Kubernetes operators",
                "eBPF observability", "zero-trust networking", "SRE practices"
            );
            case "SYSTEM_DESIGN" -> List.of(
                "consensus algorithms (Raft, Paxos)", "distributed databases",
                "global load balancing", "CAP theorem applications"
            );
            default -> List.of("production scale", "distributed systems", "operational excellence");
        };
    }

    private String buildAbsoluteBeginnerStepStrategy(String domain) {
        return switch (domain) {
            case "JAVA_BACKEND" -> """
                ABSOLUTE BEGINNER JAVA — EXACT SEQUENCE:
                Step 1: Install Java JDK 21 and IntelliJ IDEA Community Edition. Write your first Hello World program.
                Step 2: Learn variables, data types (int, String, boolean, double), and basic operators (+, -, *, /).
                Step 3: Learn control flow: if/else statements, switch, for loops, while loops. Write programs that make decisions.
                Step 4: Learn methods: how to define them, pass parameters, return values, and overload them.
                Step 5: Learn arrays and ArrayList. Practice storing and iterating over collections of data.
                Step 6: Learn Object-Oriented Programming: classes, objects, constructors, and instance variables.
                Step 7: Learn inheritance, interfaces, and polymorphism. Understand IS-A relationships.
                Step 8: Learn exception handling: try/catch/finally, checked vs unchecked exceptions.
                Step 9: Learn Java Collections: HashMap, HashSet, LinkedList. Understand when to use each.
                Step 10: Build a complete console application (e.g. student grade tracker, bank account simulator).
                DO NOT mention Spring Boot, Kafka, Docker, or any framework until all 10 steps are complete.
                """;
            case "REACT_FRONTEND" -> """
                ABSOLUTE BEGINNER FRONTEND — EXACT SEQUENCE:
                Step 1: Learn HTML: structure, headings, paragraphs, links, images, forms, semantic elements.
                Step 2: Learn CSS: selectors, box model, colors, fonts, flexbox, and CSS Grid.
                Step 3: Learn JavaScript: variables (let/const), functions, arrays, objects, DOM manipulation.
                Step 4: Learn ES6+: arrow functions, destructuring, spread operator, template literals, promises.
                Step 5: Learn npm: installing packages, package.json, running scripts.
                Step 6: Learn React basics: components, JSX syntax, rendering elements.
                Step 7: Learn React state: useState hook, event handlers, conditional rendering.
                Step 8: Learn React effects: useEffect hook, fetching data from an API.
                Step 9: Learn React forms: controlled components, form validation, form submission.
                Step 10: Build a complete React app (todo list, weather app, or movie search).
                DO NOT mention TypeScript, Next.js, Redux, or GraphQL until all 10 steps are complete.
                """;
            case "AI_ENGINEERING" -> """
                ABSOLUTE BEGINNER AI/ML — EXACT SEQUENCE:
                Step 1: Learn Python fundamentals: variables, loops, functions, lists, dictionaries.
                Step 2: Learn NumPy: creating arrays, array operations, indexing, broadcasting.
                Step 3: Learn Pandas: DataFrames, loading CSV files, filtering, grouping, basic analysis.
                Step 4: Learn Matplotlib: line plots, bar charts, scatter plots, histograms.
                Step 5: Learn statistics: mean, median, variance, standard deviation, distributions.
                Step 6: Learn scikit-learn: train/test split, linear regression, classification, evaluation metrics.
                Step 7: Understand neural networks: neurons, layers, activation functions, forward pass.
                Step 8: Learn PyTorch basics: tensors, autograd, building a simple neural network.
                Step 9: Learn the training loop: forward pass, loss calculation, backpropagation, optimizer step.
                Step 10: Build a complete classifier: image classification or text sentiment analysis.
                DO NOT mention transformers, fine-tuning, LLMs, DeepSpeed, or vLLM until all 10 steps are complete.
                """;
            default -> """
                ABSOLUTE BEGINNER SEQUENCE:
                Start with environment setup, then core language fundamentals,
                then simple projects, then basic tooling.
                Build confidence with small wins before introducing any complexity.
                Each step must be completable in 1–3 days of focused practice.
                """;
        };
    }

    private String domainDisplay(String domain) {
        return switch (domain) {
            case "JAVA_BACKEND"     -> "Java backend development";
            case "REACT_FRONTEND"   -> "React frontend development";
            case "AI_ENGINEERING"   -> "AI/ML engineering";
            case "DEVOPS"           -> "DevOps and platform engineering";
            case "DATA_ENGINEERING" -> "data engineering";
            case "SYSTEM_DESIGN"    -> "system design";
            case "CLOUD_ENGINEERING"-> "cloud engineering";
            case "CYBERSECURITY"    -> "cybersecurity";
            default                 -> "software engineering";
        };
    }
}
