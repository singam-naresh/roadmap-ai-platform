package com.assistant.service;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DomainKnowledgeEngine {

    // Domain-specific knowledge contexts
    private static final Map<String, DomainKnowledge> DOMAIN_CONTEXTS = Map.of(
        
        "AI_ENGINEERING", new DomainKnowledge(
            // Core concepts - Advanced AI/ML Engineering
            List.of("Transformer Architecture", "Multi-Head Attention", "Positional Encoding", "Layer Normalization",
                   "Gradient Accumulation", "Mixed Precision Training", "Distributed Training", "Model Parallelism",
                   "Pipeline Parallelism", "Tensor Parallelism", "Zero Redundancy Optimizer", "Gradient Checkpointing",
                   "KV Cache Optimization", "Speculative Decoding", "Beam Search", "Nucleus Sampling", "Temperature Scaling",
                   "Retrieval Augmented Generation", "Dense Passage Retrieval", "Hybrid Search", "Semantic Chunking",
                   "Embedding Drift Detection", "Reranking Models", "Cross-Encoder Architecture", "Bi-Encoder Architecture"),
            
            // Tools & Frameworks - Production AI Stack
            List.of("PyTorch 2.0+", "TensorFlow/JAX", "Hugging Face Transformers", "Hugging Face Accelerate", 
                   "DeepSpeed", "FairScale", "FSDP", "Megatron-LM", "vLLM", "TensorRT-LLM", "Triton Inference Server",
                   "ONNX Runtime", "TorchServe", "BentoML", "Ray Serve", "Seldon Core", "KServe", "MLflow", "Weights & Biases",
                   "ClearML", "Neptune", "Comet", "DVC", "Pachyderm", "Kubeflow", "MLRun", "Feast", "Tecton"),
            
            // Specialized techniques - Expert Level
            List.of("LoRA (Low-Rank Adaptation)", "QLoRA", "AdaLoRA", "PEFT (Parameter Efficient Fine-Tuning)",
                   "RLHF (Reinforcement Learning from Human Feedback)", "DPO (Direct Preference Optimization)",
                   "Constitutional AI", "RLAIF", "PPO Training", "Reward Model Training", "SFT (Supervised Fine-Tuning)",
                   "INT8/INT4 Quantization", "GPTQ", "AWQ", "SmoothQuant", "GGUF Format", "Model Sharding",
                   "Dynamic Batching", "Continuous Batching", "PagedAttention", "FlashAttention", "Memory Mapping",
                   "Tokenization Optimization", "Vocabulary Pruning", "Embedding Compression", "Knowledge Distillation",
                   "Progressive Resizing", "Curriculum Learning", "Data Augmentation", "Synthetic Data Generation"),
            
            // Project types - Production Systems
            List.of("High-Throughput LLM Inference Service", "Multi-Modal RAG System with Reranking",
                   "Distributed Fine-Tuning Pipeline", "Real-Time Embedding Service", "AI Agent Orchestration Platform",
                   "Custom Tokenizer and Vocabulary", "Model Compression and Optimization Pipeline",
                   "Federated Learning System", "AI Model Monitoring and Drift Detection", "Conversational AI with Memory",
                   "Code Generation and Completion Engine", "Document Intelligence and Extraction System")
        ),

        "JAVA_BACKEND", new DomainKnowledge(
            // Core concepts - Enterprise Java Architecture
            List.of("Hexagonal Architecture", "Clean Architecture", "Domain-Driven Design", "CQRS", "Event Sourcing",
                   "Saga Pattern", "Circuit Breaker", "Bulkhead Pattern", "Strangler Fig Pattern", "Database per Service",
                   "API Gateway Pattern", "Service Mesh", "Distributed Tracing", "Observability", "Chaos Engineering",
                   "Blue-Green Deployment", "Canary Deployment", "Feature Flags", "A/B Testing", "Load Shedding",
                   "Rate Limiting", "Backpressure", "Reactive Streams", "Non-Blocking I/O", "Connection Pooling"),
            
            // Tools & Frameworks - Production Java Stack
            List.of("Spring Boot 3.x", "Spring WebFlux", "Spring Security 6", "Spring Data JPA", "Spring Cloud Gateway",
                   "Spring Cloud Config", "Spring Cloud Sleuth", "Micrometer", "Actuator", "Maven", "Gradle",
                   "Hibernate 6", "JPA 3.0", "Flyway", "Liquibase", "TestContainers", "JUnit 5", "Mockito", "WireMock",
                   "Apache Kafka", "RabbitMQ", "Redis Cluster", "Hazelcast", "Ehcache", "PostgreSQL", "MongoDB",
                   "Elasticsearch", "InfluxDB", "Prometheus", "Grafana", "Jaeger", "Zipkin", "ELK Stack"),
            
            // Specialized techniques - Expert Level
            List.of("JVM Performance Tuning", "G1GC Optimization", "ZGC Configuration", "Shenandoah GC",
                   "JIT Compiler Optimization", "Memory Leak Detection", "Thread Dump Analysis", "Heap Dump Analysis",
                   "Connection Pool Tuning", "Database Query Optimization", "Index Strategy", "Partitioning",
                   "Sharding", "Read Replicas", "Write-Through Caching", "Write-Behind Caching", "Cache Invalidation",
                   "Distributed Locking", "Consensus Algorithms", "Eventual Consistency", "CAP Theorem Implementation",
                   "Idempotency", "Retry Mechanisms", "Dead Letter Queues", "Message Deduplication", "Transactional Outbox"),
            
            // Project types - Enterprise Systems
            List.of("High-Throughput Trading System", "Real-Time Event Processing Platform", "Multi-Tenant SaaS Backend",
                   "Distributed Payment Processing System", "Microservices with Service Mesh", "Event-Driven Architecture",
                   "CQRS with Event Sourcing", "Reactive Streaming Pipeline", "Multi-Region Active-Active System",
                   "Zero-Downtime Deployment Pipeline", "Chaos Engineering Test Suite", "Performance Monitoring Dashboard")
        ),

        "REACT_FRONTEND", new DomainKnowledge(
            // Core concepts - Modern React Architecture
            List.of("Concurrent Features", "Suspense", "Server Components", "Client Components", "Streaming SSR",
                   "Selective Hydration", "Progressive Enhancement", "Islands Architecture", "Micro-Frontends",
                   "Module Federation", "Code Splitting", "Tree Shaking", "Bundle Analysis", "Performance Budgets",
                   "Core Web Vitals", "Largest Contentful Paint", "First Input Delay", "Cumulative Layout Shift",
                   "Critical Rendering Path", "Resource Hints", "Service Workers", "Web Workers", "Intersection Observer"),
            
            // Tools & Frameworks - Production Frontend Stack
            List.of("React 18+", "Next.js 14", "Remix", "Vite", "Turbopack", "SWC", "esbuild", "Webpack 5",
                   "TypeScript 5", "Zod", "React Hook Form", "React Query/TanStack Query", "SWR", "Apollo Client",
                   "Relay", "Redux Toolkit", "Zustand", "Jotai", "Recoil", "Tailwind CSS", "Styled Components",
                   "Emotion", "Stitches", "Vanilla Extract", "Radix UI", "Headless UI", "Chakra UI", "Mantine"),
            
            // Specialized techniques - Expert Level
            List.of("React Server Components", "Streaming SSR", "Partial Hydration", "Islands Hydration",
                   "Concurrent Rendering", "Time Slicing", "Automatic Batching", "Transition API", "useDeferredValue",
                   "useTransition", "Suspense Boundaries", "Error Boundaries", "React.memo Optimization",
                   "useMemo/useCallback Patterns", "Virtual Scrolling", "Windowing", "Image Optimization",
                   "Font Optimization", "Critical CSS", "CSS-in-JS Performance", "Bundle Splitting Strategies",
                   "Prefetching", "Preloading", "Service Worker Caching", "Edge-Side Rendering", "Incremental Static Regeneration"),
            
            // Project types - Production Applications
            List.of("High-Performance E-commerce Platform", "Real-Time Collaborative Editor", "Multi-Tenant Dashboard",
                   "Progressive Web Application", "Micro-Frontend Architecture", "Design System and Component Library",
                   "Data Visualization Dashboard", "Real-Time Analytics Platform", "Video Streaming Interface",
                   "Gaming Platform Frontend", "Financial Trading Dashboard", "IoT Device Management Console")
        ),

        "DEVOPS", new DomainKnowledge(
            // Core concepts - Platform Engineering
            List.of("GitOps", "Infrastructure as Code", "Policy as Code", "Immutable Infrastructure", "Cattle vs Pets",
                   "Shift-Left Security", "Zero Trust Architecture", "Service Mesh", "Observability", "SRE Principles",
                   "Error Budgets", "SLI/SLO/SLA", "Toil Reduction", "Chaos Engineering", "Fault Injection",
                   "Circuit Breakers", "Bulkhead Pattern", "Graceful Degradation", "Blue-Green Deployment",
                   "Canary Deployment", "Rolling Updates", "Feature Flags", "Progressive Delivery"),
            
            // Tools & Frameworks - Production DevOps Stack
            List.of("Kubernetes", "Helm", "Kustomize", "ArgoCD", "Flux", "Tekton", "Jenkins X", "GitLab CI",
                   "GitHub Actions", "Terraform", "Pulumi", "Crossplane", "Ansible", "Chef", "Puppet", "SaltStack",
                   "Docker", "Podman", "Buildah", "Kaniko", "Skaffold", "Tilt", "Istio", "Linkerd", "Consul Connect",
                   "Prometheus", "Grafana", "Jaeger", "Zipkin", "OpenTelemetry", "Fluentd", "Fluent Bit", "Loki"),
            
            // Specialized techniques - Expert Level
            List.of("Multi-Cluster Management", "Cross-Cloud Deployment", "Disaster Recovery Automation",
                   "Zero-Downtime Deployments", "Database Migration Strategies", "Secret Management", "Certificate Management",
                   "Network Policy Enforcement", "Pod Security Standards", "RBAC Implementation", "Admission Controllers",
                   "Custom Resource Definitions", "Operators Development", "Webhook Configuration", "Mutating Admission",
                   "Validating Admission", "Resource Quotas", "Limit Ranges", "Horizontal Pod Autoscaling",
                   "Vertical Pod Autoscaling", "Cluster Autoscaling", "Cost Optimization", "Resource Right-Sizing"),
            
            // Project types - Platform Systems
            List.of("Multi-Tenant Kubernetes Platform", "GitOps-Based Deployment Pipeline", "Observability Stack Implementation",
                   "Service Mesh Deployment", "Multi-Cloud Infrastructure", "Disaster Recovery System",
                   "Security Compliance Automation", "Cost Optimization Platform", "Developer Self-Service Portal",
                   "Chaos Engineering Framework", "Performance Testing Pipeline", "Compliance as Code System")
        ),

        "DATA_ENGINEERING", new DomainKnowledge(
            // Core concepts
            List.of("Data Pipeline Architecture", "ETL/ELT Processes", "Data Warehousing", "Data Lakes",
                   "Stream Processing", "Batch Processing", "Data Modeling", "Data Quality", 
                   "Data Governance", "Schema Evolution", "Data Partitioning", "Data Lineage"),
            
            // Tools & Frameworks
            List.of("Apache Spark", "Apache Kafka", "Apache Airflow", "dbt", "Snowflake", "Databricks",
                   "Apache Hadoop", "Apache Flink", "Pandas", "NumPy", "SQL", "Python", "Scala",
                   "Docker", "Kubernetes", "AWS Glue", "Azure Data Factory", "Google Dataflow"),
            
            // Specialized techniques
            List.of("Data Partitioning Strategies", "Change Data Capture", "Data Deduplication",
                   "Real-time Analytics", "Data Compression", "Columnar Storage", "Data Cataloging",
                   "Data Validation", "Performance Optimization", "Cost Optimization", "Data Security"),
            
            // Project types
            List.of("Real-time Analytics Pipeline", "Data Warehouse Implementation", "ETL Pipeline Automation",
                   "Streaming Data Processing", "Data Quality Framework", "Customer 360 Platform",
                   "IoT Data Processing System", "Financial Data Pipeline", "ML Feature Store")
        ),

        "SYSTEM_DESIGN", new DomainKnowledge(
            // Core concepts
            List.of("Scalability", "Reliability", "Availability", "Consistency", "Partition Tolerance",
                   "Load Balancing", "Caching", "Database Sharding", "Replication", "Distributed Systems",
                   "Microservices", "Event-Driven Architecture", "CQRS", "Event Sourcing"),
            
            // Tools & Frameworks
            List.of("Load Balancers", "CDN", "Message Queues", "Databases", "Caching Systems",
                   "Monitoring Tools", "API Gateways", "Service Mesh", "Container Orchestration",
                   "Cloud Services", "Distributed Databases", "Search Engines", "Analytics Platforms"),
            
            // Specialized techniques
            List.of("Horizontal vs Vertical Scaling", "Database Partitioning", "Consistent Hashing",
                   "Circuit Breaker Pattern", "Bulkhead Pattern", "Saga Pattern", "Two-Phase Commit",
                   "Eventually Consistent Systems", "CAP Theorem Applications", "Rate Limiting",
                   "Data Denormalization", "Read Replicas", "Write-through vs Write-back Caching"),
            
            // Project types
            List.of("High-Traffic Web Application", "Distributed Chat System", "Real-time Analytics Platform",
                   "Content Delivery Network", "Ride-sharing System", "Social Media Platform",
                   "E-commerce Marketplace", "Video Streaming Service", "IoT Data Collection System")
        )
    );

    public DomainKnowledge getDomainKnowledge(String domainKey) {
        return DOMAIN_CONTEXTS.getOrDefault(domainKey, getGeneralKnowledge());
    }

    public String buildDomainContext(String primaryDomain, String secondaryDomain, String detectedExpertiseLevel) {
        StringBuilder context = new StringBuilder();
        
        DomainKnowledge primary = getDomainKnowledge(primaryDomain);
        context.append("DOMAIN EXPERTISE: ").append(primaryDomain).append("\n");
        context.append("EXPERTISE LEVEL: ").append(detectedExpertiseLevel).append("\n\n");
        
        // Adjust content depth based on expertise level
        if ("EXPERT".equals(detectedExpertiseLevel) || "ADVANCED".equals(detectedExpertiseLevel)) {
            context.append("ADVANCED CONCEPTS TO INTEGRATE:\n");
            primary.getConcepts().forEach(concept -> context.append("- ").append(concept).append("\n"));
            
            context.append("\nPRODUCTION TOOLS & FRAMEWORKS:\n");
            primary.getTools().forEach(tool -> context.append("- ").append(tool).append("\n"));
            
            context.append("\nEXPERT-LEVEL TECHNIQUES:\n");
            primary.getTechniques().forEach(technique -> context.append("- ").append(technique).append("\n"));
            
            context.append("\nPRODUCTION PROJECT EXAMPLES:\n");
            primary.getProjects().forEach(project -> context.append("- ").append(project).append("\n"));
        } else {
            // For intermediate/beginner, use subset of concepts
            context.append("CORE CONCEPTS TO INTEGRATE:\n");
            primary.getConcepts().stream().limit(8)
                .forEach(concept -> context.append("- ").append(concept).append("\n"));
            
            context.append("\nESSENTIAL TOOLS & FRAMEWORKS:\n");
            primary.getTools().stream().limit(10)
                .forEach(tool -> context.append("- ").append(tool).append("\n"));
            
            context.append("\nKEY TECHNIQUES:\n");
            primary.getTechniques().stream().limit(8)
                .forEach(technique -> context.append("- ").append(technique).append("\n"));
        }
        
        if (secondaryDomain != null && !secondaryDomain.equals(primaryDomain)) {
            DomainKnowledge secondary = getDomainKnowledge(secondaryDomain);
            context.append("\nCROSS-DOMAIN INTEGRATION (").append(secondaryDomain).append("):\n");
            secondary.getConcepts().stream().limit(5)
                .forEach(concept -> context.append("- ").append(concept).append("\n"));
        }
        
        return context.toString();
    }

    public String detectExpertiseLevel(String userInput) {
        String input = userInput.toLowerCase();
        
        // Expert level indicators
        String[] expertKeywords = {
            "advanced", "expert", "senior", "principal", "staff", "architect", "lead",
            "production", "enterprise", "scale", "optimization", "performance", "distributed",
            "microservices", "infrastructure", "deployment", "monitoring", "observability"
        };
        
        // Advanced technical terms
        String[] advancedTerms = {
            "lora", "qlora", "rlhf", "dpo", "peft", "tensorrt", "triton", "vllm",
            "kubernetes", "istio", "prometheus", "grafana", "terraform", "helm",
            "react server components", "suspense", "concurrent", "streaming ssr",
            "circuit breaker", "saga pattern", "cqrs", "event sourcing", "sharding"
        };
        
        long expertCount = Arrays.stream(expertKeywords)
            .mapToLong(keyword -> input.split(keyword, -1).length - 1)
            .sum();
            
        long advancedCount = Arrays.stream(advancedTerms)
            .mapToLong(term -> input.split(term, -1).length - 1)
            .sum();
        
        if (expertCount >= 2 || advancedCount >= 3) return "EXPERT";
        if (expertCount >= 1 || advancedCount >= 2) return "ADVANCED";
        if (input.contains("intermediate") || input.length() > 100) return "INTERMEDIATE";
        return "BEGINNER";
    }

    private DomainKnowledge getGeneralKnowledge() {
        return new DomainKnowledge(
            List.of("Problem Solving", "Software Development", "Best Practices", "Testing", "Documentation"),
            List.of("Git", "IDE", "Command Line", "Debugging Tools", "Project Management Tools"),
            List.of("Code Review", "Version Control", "Agile Methodology", "Technical Documentation"),
            List.of("Personal Portfolio", "Open Source Contribution", "Technical Blog", "Learning Project")
        );
    }

    // Domain knowledge container
    public static class DomainKnowledge {
        private final List<String> concepts;
        private final List<String> tools;
        private final List<String> techniques;
        private final List<String> projects;

        public DomainKnowledge(List<String> concepts, List<String> tools, 
                             List<String> techniques, List<String> projects) {
            this.concepts = concepts;
            this.tools = tools;
            this.techniques = techniques;
            this.projects = projects;
        }

        public List<String> getConcepts() { return concepts; }
        public List<String> getTools() { return tools; }
        public List<String> getTechniques() { return techniques; }
        public List<String> getProjects() { return projects; }
    }
}