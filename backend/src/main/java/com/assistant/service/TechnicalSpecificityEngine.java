package com.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class TechnicalSpecificityEngine {

    private static final Logger log = LoggerFactory.getLogger(TechnicalSpecificityEngine.class);

    // Generic phrases that should be replaced with concrete technologies
    private static final Map<String, List<String>> GENERIC_TO_CONCRETE;
    static {
        GENERIC_TO_CONCRETE = new HashMap<>();
        GENERIC_TO_CONCRETE.put("message queue", List.of("Apache Kafka", "RabbitMQ", "Apache Pulsar", "NATS", "Amazon SQS"));
        GENERIC_TO_CONCRETE.put("message queues", List.of("Apache Kafka", "RabbitMQ", "Apache Pulsar", "NATS", "Amazon SQS"));
        GENERIC_TO_CONCRETE.put("container orchestration", List.of("Kubernetes", "Docker Swarm", "Amazon ECS", "HashiCorp Nomad"));
        GENERIC_TO_CONCRETE.put("container orchestration tools", List.of("Kubernetes", "Docker Swarm", "Amazon ECS", "HashiCorp Nomad"));
        GENERIC_TO_CONCRETE.put("cloud-native database", List.of("CockroachDB", "Amazon Aurora", "Google Cloud Spanner", "Azure Cosmos DB"));
        GENERIC_TO_CONCRETE.put("cloud database", List.of("CockroachDB", "Amazon Aurora", "Google Cloud Spanner", "Azure Cosmos DB"));
        GENERIC_TO_CONCRETE.put("monitoring system", List.of("Prometheus + Grafana", "DataDog", "New Relic", "Elastic APM"));
        GENERIC_TO_CONCRETE.put("monitoring tools", List.of("Prometheus + Grafana", "DataDog", "New Relic", "Elastic APM"));
        GENERIC_TO_CONCRETE.put("service mesh", List.of("Istio", "Linkerd", "Consul Connect", "AWS App Mesh"));
        GENERIC_TO_CONCRETE.put("load balancer", List.of("Envoy Proxy", "HAProxy", "NGINX", "AWS Application Load Balancer"));
        GENERIC_TO_CONCRETE.put("caching layer", List.of("Redis Cluster", "Memcached", "Hazelcast", "Apache Ignite"));
        GENERIC_TO_CONCRETE.put("database", List.of("PostgreSQL", "MongoDB", "Cassandra", "Redis"));
        GENERIC_TO_CONCRETE.put("container platform", List.of("Kubernetes", "OpenShift", "Docker Enterprise", "Rancher"));
        GENERIC_TO_CONCRETE.put("cloud service", List.of("AWS", "Google Cloud Platform", "Microsoft Azure", "DigitalOcean"));
        GENERIC_TO_CONCRETE.put("CI/CD pipeline", List.of("Jenkins", "GitHub Actions", "GitLab CI", "Azure DevOps"));
        GENERIC_TO_CONCRETE.put("infrastructure as code", List.of("Terraform", "Pulumi", "AWS CloudFormation", "Azure ARM Templates"));
    }

    // Domain-specific concrete technologies
    private static final Map<String, ConcreteImplementations> DOMAIN_IMPLEMENTATIONS;
    static {
        DOMAIN_IMPLEMENTATIONS = new HashMap<>();
        
        DOMAIN_IMPLEMENTATIONS.put("DISTRIBUTED_SYSTEMS", new ConcreteImplementations(
            // Message Systems
            List.of("Apache Kafka", "Apache Pulsar", "NATS", "RabbitMQ", "Amazon SQS", "Google Pub/Sub"),
            // Service Communication
            List.of("gRPC", "Apache Thrift", "GraphQL", "REST with OpenAPI", "WebSocket", "Server-Sent Events"),
            // Service Discovery & Mesh
            List.of("Envoy Proxy", "Istio", "Linkerd", "Consul", "etcd", "Apache Zookeeper"),
            // Consensus & Coordination
            List.of("Raft consensus", "Paxos algorithm", "PBFT", "distributed locks", "leader election"),
            // Data Patterns
            List.of("CQRS", "Event Sourcing", "Saga Pattern", "Outbox Pattern", "Two-Phase Commit"),
            // Storage Systems
            List.of("Redis Cluster", "Apache Cassandra", "CockroachDB", "TiDB", "ScyllaDB"),
            // Algorithms & Techniques
            List.of("consistent hashing", "quorum reads/writes", "vector clocks", "Bloom filters", 
                   "dead-letter queues", "backpressure handling", "circuit breaker", "bulkhead pattern")
        ));

        DOMAIN_IMPLEMENTATIONS.put("AI_INFRASTRUCTURE", new ConcreteImplementations(
            // Training Frameworks
            List.of("DeepSpeed ZeRO-3", "FairScale FSDP", "Megatron-LM", "Horovod", "PyTorch DDP"),
            // Fine-tuning Techniques
            List.of("QLoRA", "LoRA", "AdaLoRA", "PEFT", "Prefix Tuning", "P-Tuning v2"),
            // Training Methods
            List.of("RLHF", "DPO", "Constitutional AI", "RLAIF", "PPO", "SFT"),
            // Inference Optimization
            List.of("vLLM", "Triton Inference Server", "TensorRT-LLM", "ONNX Runtime", "TorchServe"),
            // Attention & Memory
            List.of("FlashAttention", "PagedAttention", "speculative decoding", "KV cache optimization"),
            // RAG & Retrieval
            List.of("semantic chunking", "reranking pipeline", "hybrid retrieval", "dense passage retrieval",
                   "embedding drift detection", "cross-encoder reranking", "bi-encoder retrieval"),
            // Quantization & Compression
            List.of("INT8 quantization", "INT4 quantization", "GPTQ", "AWQ", "SmoothQuant", "GGUF format"),
            // Distributed Training
            List.of("gradient accumulation", "mixed precision training", "model parallelism", "pipeline parallelism")
        ));

        DOMAIN_IMPLEMENTATIONS.put("FRONTEND_ARCHITECTURE", new ConcreteImplementations(
            // React Patterns
            List.of("React Server Components", "Suspense boundaries", "streaming SSR", "selective hydration"),
            // State Management
            List.of("Redux Toolkit", "Zustand", "Jotai", "React Query", "SWR", "Apollo Client"),
            // Performance
            List.of("code splitting", "bundle optimization", "tree shaking", "lazy loading", "virtualization"),
            // Rendering Strategies
            List.of("Static Site Generation", "Incremental Static Regeneration", "edge rendering", "client-side rendering"),
            // Build Tools
            List.of("Vite", "Webpack 5", "Turbopack", "esbuild", "SWC", "Rollup"),
            // Optimization Techniques
            List.of("Critical CSS", "resource hints", "service worker caching", "HTTP/2 push", "prefetching"),
            // Monitoring & Analytics
            List.of("Core Web Vitals", "Real User Monitoring", "Lighthouse CI", "Bundle Analyzer"),
            // Architecture Patterns
            List.of("Micro-frontends", "Module Federation", "Islands Architecture", "Progressive Enhancement")
        ));

        DOMAIN_IMPLEMENTATIONS.put("DEVOPS_PLATFORM", new ConcreteImplementations(
            // Container Orchestration
            List.of("Kubernetes", "Docker Swarm", "Amazon ECS", "HashiCorp Nomad", "OpenShift"),
            // GitOps & Deployment
            List.of("ArgoCD", "Flux CD", "Tekton", "Jenkins X", "Spinnaker", "Harness"),
            // Infrastructure as Code
            List.of("Terraform", "Pulumi", "AWS CloudFormation", "Azure ARM Templates", "Google Deployment Manager"),
            // Service Mesh & Networking
            List.of("Istio", "Linkerd", "Consul Connect", "AWS App Mesh", "Envoy Proxy"),
            // Observability Stack
            List.of("Prometheus + Grafana", "OpenTelemetry", "Jaeger", "Zipkin", "Elastic APM"),
            // Security & Compliance
            List.of("Falco", "OPA Gatekeeper", "Twistlock", "Aqua Security", "Vault"),
            // Deployment Strategies
            List.of("blue-green deployment", "canary deployment", "rolling updates", "feature flags"),
            // Automation & Orchestration
            List.of("Ansible", "Chef", "Puppet", "SaltStack", "Helm charts", "Kustomize")
        ));
    }

    // Forbidden generic phrases that trigger rejection
    private static final Set<String> FORBIDDEN_GENERIC_PHRASES = Set.of(
        "database system", "message queue", "monitoring tools", "container platform",
        "cloud service", "caching layer", "load balancer", "service mesh",
        "orchestration tool", "deployment tool", "monitoring system", "logging system",
        "storage solution", "compute platform", "networking solution", "security tool",
        "automation tool", "configuration management", "infrastructure tool",
        "development framework", "testing framework", "build system"
    );

    // Production architecture concerns that should be automatically injected
    private static final Map<String, List<String>> PRODUCTION_CONCERNS;
    static {
        PRODUCTION_CONCERNS = new HashMap<>();
        PRODUCTION_CONCERNS.put("SCALABILITY", List.of("horizontal scaling", "auto-scaling", "load balancing", "sharding", "partitioning"));
        PRODUCTION_CONCERNS.put("RELIABILITY", List.of("fault tolerance", "circuit breakers", "retry mechanisms", "graceful degradation"));
        PRODUCTION_CONCERNS.put("OBSERVABILITY", List.of("distributed tracing", "metrics collection", "log aggregation", "alerting"));
        PRODUCTION_CONCERNS.put("SECURITY", List.of("authentication", "authorization", "encryption", "network policies", "secrets management"));
        PRODUCTION_CONCERNS.put("PERFORMANCE", List.of("caching strategies", "connection pooling", "query optimization", "CDN"));
        PRODUCTION_CONCERNS.put("DEPLOYMENT", List.of("CI/CD pipelines", "infrastructure as code", "configuration management", "rollback strategies"));
    }

    public SpecificityAnalysis analyzeSpecificity(String content, String domain) {
        SpecificityAnalysis analysis = new SpecificityAnalysis();
        
        String lowerContent = content.toLowerCase();
        
        // Count generic phrases
        int genericCount = 0;
        List<String> foundGeneric = new ArrayList<>();
        for (String generic : FORBIDDEN_GENERIC_PHRASES) {
            if (lowerContent.contains(generic)) {
                genericCount++;
                foundGeneric.add(generic);
            }
        }
        
        // Count concrete technologies
        int concreteCount = 0;
        List<String> foundConcrete = new ArrayList<>();
        
        // Check domain-specific implementations
        String domainKey = mapDomainToKey(domain);
        ConcreteImplementations implementations = DOMAIN_IMPLEMENTATIONS.get(domainKey);
        if (implementations != null) {
            for (List<String> techList : implementations.getAllTechnologies()) {
                for (String tech : techList) {
                    if (lowerContent.contains(tech.toLowerCase())) {
                        concreteCount++;
                        foundConcrete.add(tech);
                    }
                }
            }
        }
        
        // Calculate specificity score
        int totalWords = content.split("\\s+").length;
        double genericRatio = (double) genericCount / Math.max(totalWords / 10, 1); // Per 10 words
        double concreteRatio = (double) concreteCount / Math.max(totalWords / 20, 1); // Per 20 words
        
        analysis.specificityScore = Math.max(0, Math.min(1, concreteRatio - genericRatio));
        analysis.genericPhraseCount = genericCount;
        analysis.concreteTechnologyCount = concreteCount;
        analysis.foundGenericPhrases = foundGeneric;
        analysis.foundConcreteTechnologies = foundConcrete;
        
        // Determine if acceptable
        analysis.isAcceptable = genericRatio < 0.25 && concreteRatio > 0.1 && analysis.specificityScore > 0.3;
        
        log.info("[specificity] Domain: {} | Score: {:.2f} | Generic: {} | Concrete: {} | Acceptable: {}", 
                domain, analysis.specificityScore, genericCount, concreteCount, analysis.isAcceptable);
        
        return analysis;
    }

    public String enhanceWithConcreteImplementations(String content, String domain) {
        String enhanced = content;
        
        // Replace generic phrases with concrete implementations
        for (Map.Entry<String, List<String>> entry : GENERIC_TO_CONCRETE.entrySet()) {
            String generic = entry.getKey();
            List<String> concrete = entry.getValue();
            
            if (enhanced.toLowerCase().contains(generic)) {
                // Pick a random concrete implementation
                String replacement = concrete.get(new Random().nextInt(concrete.size()));
                enhanced = enhanced.replaceAll("(?i)" + Pattern.quote(generic), replacement);
            }
        }
        
        return enhanced;
    }

    public List<String> generateProductionConcerns(String domain) {
        List<String> concerns = new ArrayList<>();
        
        // Add domain-appropriate production concerns
        switch (domain.toUpperCase()) {
            case "AI_ENGINEERING":
                concerns.addAll(List.of(
                    "model serving latency optimization",
                    "GPU memory management",
                    "inference throughput scaling",
                    "model versioning and A/B testing",
                    "distributed training coordination"
                ));
                break;
            case "JAVA_BACKEND":
                concerns.addAll(List.of(
                    "JVM heap optimization",
                    "connection pool tuning",
                    "distributed transaction management",
                    "service discovery and health checks",
                    "API rate limiting and throttling"
                ));
                break;
            case "DEVOPS":
                concerns.addAll(List.of(
                    "multi-cluster orchestration",
                    "zero-downtime deployments",
                    "infrastructure drift detection",
                    "security policy enforcement",
                    "cost optimization and resource right-sizing"
                ));
                break;
            default:
                concerns.addAll(PRODUCTION_CONCERNS.get("SCALABILITY"));
                concerns.addAll(PRODUCTION_CONCERNS.get("RELIABILITY"));
        }
        
        return concerns;
    }

    private String mapDomainToKey(String domain) {
        return switch (domain.toUpperCase()) {
            case "AI_ENGINEERING" -> "AI_INFRASTRUCTURE";
            case "JAVA_BACKEND", "SYSTEM_DESIGN" -> "DISTRIBUTED_SYSTEMS";
            case "REACT_FRONTEND" -> "FRONTEND_ARCHITECTURE";
            case "DEVOPS", "CLOUD_ENGINEERING" -> "DEVOPS_PLATFORM";
            default -> "DISTRIBUTED_SYSTEMS";
        };
    }

    // Data classes
    public static class SpecificityAnalysis {
        public double specificityScore = 0.0;
        public int genericPhraseCount = 0;
        public int concreteTechnologyCount = 0;
        public List<String> foundGenericPhrases = new ArrayList<>();
        public List<String> foundConcreteTechnologies = new ArrayList<>();
        public boolean isAcceptable = false;
        public List<String> suggestions = new ArrayList<>();
    }

    public static class ConcreteImplementations {
        private final List<List<String>> allTechnologies;

        public ConcreteImplementations(List<String>... technologyLists) {
            this.allTechnologies = Arrays.asList(technologyLists);
        }

        public List<List<String>> getAllTechnologies() {
            return allTechnologies;
        }
    }
}