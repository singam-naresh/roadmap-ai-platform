package com.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TechnologyOntologyEngine {

    private static final Logger log = LoggerFactory.getLogger(TechnologyOntologyEngine.class);

    // Technology ontology with semantic relationships
    private static final Map<String, TechnologyDefinition> TECHNOLOGY_ONTOLOGY;
    
    static {
        TECHNOLOGY_ONTOLOGY = new HashMap<>();
        
        // ===== ORCHESTRATION LAYER =====
        TECHNOLOGY_ONTOLOGY.put("Kubernetes", new TechnologyDefinition(
            "Kubernetes", "orchestration", "container_orchestration", "platform",
            Set.of("containers", "docker"), // dependencies
            Set.of("Helm", "Istio", "ArgoCD", "Prometheus", "Grafana", "Jaeger"), // complements
            Set.of("ECS", "Docker Swarm", "Nomad", "OpenShift"), // alternatives
            Set.of("Lambda", "Vercel", "Firebase"), // conflicts
            "infrastructure"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("ECS", new TechnologyDefinition(
            "ECS", "orchestration", "container_orchestration", "platform",
            Set.of("AWS", "containers"), 
            Set.of("ALB", "CloudWatch", "ECR", "Fargate"),
            Set.of("Kubernetes", "Docker Swarm", "Nomad"),
            Set.of("GCP", "Azure", "Vercel"),
            "infrastructure"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("Docker Swarm", new TechnologyDefinition(
            "Docker Swarm", "orchestration", "container_orchestration", "platform",
            Set.of("Docker"),
            Set.of("Docker Compose", "Docker Registry"),
            Set.of("Kubernetes", "ECS", "Nomad"),
            Set.of("Lambda", "Vercel"),
            "infrastructure"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("Nomad", new TechnologyDefinition(
            "Nomad", "orchestration", "container_orchestration", "platform",
            Set.of("containers"),
            Set.of("Consul", "Vault", "Terraform"),
            Set.of("Kubernetes", "ECS", "Docker Swarm"),
            Set.of("Lambda", "Vercel"),
            "infrastructure"
        ));
        
        // ===== KUBERNETES ECOSYSTEM =====
        TECHNOLOGY_ONTOLOGY.put("Helm", new TechnologyDefinition(
            "Helm", "package_management", "kubernetes_package_manager", "tool",
            Set.of("Kubernetes"), // requires Kubernetes
            Set.of("ArgoCD", "Kustomize"),
            Set.of("Kustomize", "Jsonnet"), // alternatives for k8s templating
            Set.of("Docker Compose", "Terraform"), // conflicts with different paradigms
            "deployment"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("Istio", new TechnologyDefinition(
            "Istio", "service_mesh", "service_mesh", "platform",
            Set.of("Kubernetes"),
            Set.of("Envoy", "Jaeger", "Prometheus", "Grafana"),
            Set.of("Linkerd", "Consul Connect", "AWS App Mesh"),
            Set.of("API Gateway", "ALB"), // conflicts with different networking approaches
            "networking"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("ArgoCD", new TechnologyDefinition(
            "ArgoCD", "gitops", "continuous_deployment", "tool",
            Set.of("Kubernetes", "Git"),
            Set.of("Helm", "Kustomize"),
            Set.of("Flux CD", "Jenkins X", "Tekton"),
            Set.of("CodePipeline", "GitHub Actions"), // conflicts with different CD approaches
            "deployment"
        ));
        
        // ===== MESSAGING LAYER =====
        TECHNOLOGY_ONTOLOGY.put("Apache Kafka", new TechnologyDefinition(
            "Apache Kafka", "messaging", "event_streaming", "platform",
            Set.of("JVM", "Zookeeper"),
            Set.of("Schema Registry", "Kafka Connect", "KSQL", "Confluent Control Center"),
            Set.of("Apache Pulsar", "NATS", "Redpanda"),
            Set.of("SQS", "RabbitMQ", "Redis Pub/Sub"), // conflicts with different messaging paradigms
            "messaging"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("Schema Registry", new TechnologyDefinition(
            "Schema Registry", "schema_management", "schema_registry", "tool",
            Set.of("Apache Kafka"),
            Set.of("Kafka Connect", "KSQL"),
            Set.of("Apicurio Registry", "AWS Glue Schema Registry"),
            Set.of("REST APIs", "GraphQL"), // conflicts with schema-less approaches
            "messaging"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("SQS", new TechnologyDefinition(
            "SQS", "messaging", "message_queue", "service",
            Set.of("AWS"),
            Set.of("SNS", "Lambda", "CloudWatch"),
            Set.of("RabbitMQ", "Azure Service Bus", "Google Pub/Sub"),
            Set.of("Apache Kafka", "Apache Pulsar"), // conflicts with streaming platforms
            "messaging"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("RabbitMQ", new TechnologyDefinition(
            "RabbitMQ", "messaging", "message_broker", "platform",
            Set.of("AMQP"),
            Set.of("RabbitMQ Management", "Shovel", "Federation"),
            Set.of("Apache ActiveMQ", "Azure Service Bus", "Google Pub/Sub"),
            Set.of("Apache Kafka", "Redis"), // conflicts with different messaging patterns
            "messaging"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("Kinesis", new TechnologyDefinition(
            "Kinesis", "messaging", "stream_processing", "service",
            Set.of("AWS"),
            Set.of("Kinesis Analytics", "Kinesis Firehose", "Lambda"),
            Set.of("Apache Kafka", "Apache Pulsar", "Google Pub/Sub"),
            Set.of("SQS", "RabbitMQ"), // conflicts with traditional message queues
            "messaging"
        ));
        
        // ===== DATABASE LAYER =====
        TECHNOLOGY_ONTOLOGY.put("PostgreSQL", new TechnologyDefinition(
            "PostgreSQL", "database", "relational_database", "platform",
            Set.of("SQL"),
            Set.of("PgBouncer", "pg_stat_statements", "PostGIS", "Citus"),
            Set.of("MySQL", "MariaDB", "CockroachDB"),
            Set.of("MongoDB", "Cassandra", "Redis"), // conflicts with NoSQL paradigms
            "storage"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("PgBouncer", new TechnologyDefinition(
            "PgBouncer", "connection_pooling", "database_proxy", "tool",
            Set.of("PostgreSQL"),
            Set.of("pgpool-II", "HAProxy"),
            Set.of("pgpool-II", "Odyssey"),
            Set.of("MongoDB", "Redis"), // conflicts with different database types
            "storage"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("MongoDB", new TechnologyDefinition(
            "MongoDB", "database", "document_database", "platform",
            Set.of("BSON"),
            Set.of("MongoDB Compass", "MongoDB Atlas", "Mongoose"),
            Set.of("CouchDB", "Amazon DocumentDB", "Azure Cosmos DB"),
            Set.of("PostgreSQL", "MySQL"), // conflicts with relational paradigms
            "storage"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("Redis", new TechnologyDefinition(
            "Redis", "cache", "in_memory_database", "platform",
            Set.of(),
            Set.of("Redis Sentinel", "Redis Cluster", "RedisInsight"),
            Set.of("Memcached", "Hazelcast", "KeyDB"),
            Set.of("PostgreSQL", "MongoDB"), // conflicts when used as primary database
            "storage"
        ));
        
        // ===== MONITORING LAYER =====
        TECHNOLOGY_ONTOLOGY.put("Prometheus", new TechnologyDefinition(
            "Prometheus", "monitoring", "metrics_collection", "platform",
            Set.of(),
            Set.of("Grafana", "AlertManager", "Node Exporter", "Jaeger"),
            Set.of("DataDog", "New Relic", "CloudWatch"),
            Set.of("Splunk", "ELK Stack"), // conflicts with different monitoring approaches
            "observability"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("Grafana", new TechnologyDefinition(
            "Grafana", "visualization", "dashboard", "tool",
            Set.of("data_source"), // requires some data source
            Set.of("Prometheus", "InfluxDB", "Elasticsearch"),
            Set.of("Kibana", "DataDog", "New Relic"),
            Set.of("Splunk", "Tableau"), // conflicts with different visualization paradigms
            "observability"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("CloudWatch", new TechnologyDefinition(
            "CloudWatch", "monitoring", "aws_monitoring", "service",
            Set.of("AWS"),
            Set.of("X-Ray", "CloudTrail", "AWS Config"),
            Set.of("Azure Monitor", "Google Cloud Monitoring"),
            Set.of("Prometheus", "DataDog"), // conflicts with third-party monitoring
            "observability"
        ));
        
        // ===== FRONTEND LAYER =====
        TECHNOLOGY_ONTOLOGY.put("React", new TechnologyDefinition(
            "React", "frontend", "ui_library", "library",
            Set.of("JavaScript", "Node.js"),
            Set.of("Next.js", "Redux", "React Router", "Styled Components"),
            Set.of("Vue.js", "Angular", "Svelte"),
            Set.of("jQuery", "Vanilla JS"), // conflicts with different frontend paradigms
            "frontend"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("Next.js", new TechnologyDefinition(
            "Next.js", "frontend", "react_framework", "framework",
            Set.of("React", "Node.js"),
            Set.of("Vercel", "Tailwind CSS", "TypeScript"),
            Set.of("Nuxt.js", "Gatsby", "Remix"),
            Set.of("Vue.js", "Angular"), // conflicts with different frontend frameworks
            "frontend"
        ));
        
        // ===== AI/ML LAYER =====
        TECHNOLOGY_ONTOLOGY.put("vLLM", new TechnologyDefinition(
            "vLLM", "ai_inference", "llm_serving", "platform",
            Set.of("Python", "CUDA"),
            Set.of("FastAPI", "Ray Serve", "Prometheus"),
            Set.of("TorchServe", "Triton Inference Server", "TensorRT-LLM"),
            Set.of("Flask", "Django"), // conflicts with general web frameworks for ML serving
            "ai_inference"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("TensorRT-LLM", new TechnologyDefinition(
            "TensorRT-LLM", "ai_optimization", "llm_optimization", "tool",
            Set.of("NVIDIA GPU", "CUDA"),
            Set.of("Triton Inference Server", "vLLM"),
            Set.of("ONNX Runtime", "OpenVINO"),
            Set.of("CPU inference", "PyTorch native"), // conflicts with unoptimized inference
            "ai_inference"
        ));
        
        // ===== CLOUD PLATFORMS =====
        TECHNOLOGY_ONTOLOGY.put("AWS", new TechnologyDefinition(
            "AWS", "cloud", "cloud_platform", "platform",
            Set.of(),
            Set.of("ECS", "Lambda", "S3", "RDS", "CloudWatch"),
            Set.of("GCP", "Azure", "DigitalOcean"),
            Set.of("on-premise", "bare metal"), // conflicts with self-hosted approaches
            "infrastructure"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("GCP", new TechnologyDefinition(
            "GCP", "cloud", "cloud_platform", "platform",
            Set.of(),
            Set.of("GKE", "Cloud Functions", "Cloud Storage", "Cloud SQL", "Cloud Monitoring"),
            Set.of("AWS", "Azure", "DigitalOcean"),
            Set.of("on-premise", "bare metal"),
            "infrastructure"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("Azure", new TechnologyDefinition(
            "Azure", "cloud", "cloud_platform", "platform",
            Set.of(),
            Set.of("AKS", "Azure Functions", "Blob Storage", "Azure SQL", "Azure Monitor"),
            Set.of("AWS", "GCP", "DigitalOcean"),
            Set.of("on-premise", "bare metal"),
            "infrastructure"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("Vercel", new TechnologyDefinition(
            "Vercel", "deployment", "frontend_platform", "service",
            Set.of("JavaScript", "Node.js"),
            Set.of("Next.js", "React", "TypeScript"),
            Set.of("Netlify", "AWS Amplify", "Firebase Hosting"),
            Set.of("Kubernetes", "Docker"), // conflicts with container-based deployment
            "deployment"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("Netlify", new TechnologyDefinition(
            "Netlify", "deployment", "frontend_platform", "service",
            Set.of("JavaScript"),
            Set.of("React", "Vue.js", "Gatsby", "Nuxt.js"),
            Set.of("Vercel", "AWS Amplify", "Firebase Hosting"),
            Set.of("Kubernetes", "Docker"),
            "deployment"
        ));
        
        // ===== BUILD TOOLS =====
        TECHNOLOGY_ONTOLOGY.put("Webpack", new TechnologyDefinition(
            "Webpack", "build_tools", "module_bundler", "tool",
            Set.of("Node.js"),
            Set.of("Babel", "TypeScript", "ESLint"),
            Set.of("Vite", "Rollup", "Parcel"),
            Set.of("Browserify", "RequireJS"),
            "frontend"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("Vite", new TechnologyDefinition(
            "Vite", "build_tools", "module_bundler", "tool",
            Set.of("Node.js"),
            Set.of("Vue.js", "React", "TypeScript"),
            Set.of("Webpack", "Rollup", "Parcel"),
            Set.of("Browserify", "RequireJS"),
            "frontend"
        ));
        
        // ===== TESTING FRAMEWORKS =====
        TECHNOLOGY_ONTOLOGY.put("Jest", new TechnologyDefinition(
            "Jest", "testing", "test_framework", "tool",
            Set.of("Node.js"),
            Set.of("React Testing Library", "Enzyme", "Babel"),
            Set.of("Vitest", "Mocha", "Jasmine"),
            Set.of("Karma", "QUnit"),
            "frontend"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("Vitest", new TechnologyDefinition(
            "Vitest", "testing", "test_framework", "tool",
            Set.of("Vite", "Node.js"),
            Set.of("Vue Test Utils", "React Testing Library"),
            Set.of("Jest", "Mocha", "Jasmine"),
            Set.of("Karma", "QUnit"),
            "frontend"
        ));
        
        // ===== ADDITIONAL KUBERNETES ECOSYSTEM =====
        TECHNOLOGY_ONTOLOGY.put("Kustomize", new TechnologyDefinition(
            "Kustomize", "package_management", "kubernetes_templating", "tool",
            Set.of("Kubernetes"),
            Set.of("ArgoCD", "Flux CD"),
            Set.of("Helm", "Jsonnet"),
            Set.of("Docker Compose", "Terraform"),
            "deployment"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("Linkerd", new TechnologyDefinition(
            "Linkerd", "service_mesh", "service_mesh", "platform",
            Set.of("Kubernetes"),
            Set.of("Prometheus", "Grafana", "Jaeger"),
            Set.of("Istio", "Consul Connect", "AWS App Mesh"),
            Set.of("API Gateway", "ALB"),
            "networking"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("Jaeger", new TechnologyDefinition(
            "Jaeger", "observability", "distributed_tracing", "tool",
            Set.of(),
            Set.of("Prometheus", "Grafana", "OpenTelemetry"),
            Set.of("Zipkin", "AWS X-Ray", "DataDog APM"),
            Set.of("New Relic", "AppDynamics"),
            "observability"
        ));
        
        // ===== ADDITIONAL MESSAGING =====
        TECHNOLOGY_ONTOLOGY.put("Apache Pulsar", new TechnologyDefinition(
            "Apache Pulsar", "messaging", "event_streaming", "platform",
            Set.of("JVM"),
            Set.of("Pulsar Functions", "BookKeeper", "Pulsar SQL"),
            Set.of("Apache Kafka", "NATS", "Redpanda"),
            Set.of("SQS", "RabbitMQ", "Redis Pub/Sub"),
            "messaging"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("NATS", new TechnologyDefinition(
            "NATS", "messaging", "message_broker", "platform",
            Set.of(),
            Set.of("NATS Streaming", "JetStream", "NATS CLI"),
            Set.of("Apache Kafka", "RabbitMQ", "Apache Pulsar"),
            Set.of("SQS", "SNS"),
            "messaging"
        ));
        
        // ===== ADDITIONAL DATABASES =====
        TECHNOLOGY_ONTOLOGY.put("CockroachDB", new TechnologyDefinition(
            "CockroachDB", "database", "distributed_database", "platform",
            Set.of("SQL"),
            Set.of("CockroachDB Cloud", "CRDB CLI", "Backup/Restore"),
            Set.of("TiDB", "YugabyteDB", "Google Spanner"),
            Set.of("MongoDB", "Cassandra", "Redis"),
            "storage"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("Cassandra", new TechnologyDefinition(
            "Cassandra", "database", "wide_column_database", "platform",
            Set.of("JVM"),
            Set.of("DataStax", "Cassandra Query Language", "nodetool"),
            Set.of("ScyllaDB", "HBase", "DynamoDB"),
            Set.of("PostgreSQL", "MySQL", "MongoDB"),
            "storage"
        ));
        
        // ===== ADDITIONAL CACHING =====
        TECHNOLOGY_ONTOLOGY.put("Memcached", new TechnologyDefinition(
            "Memcached", "cache", "in_memory_cache", "platform",
            Set.of(),
            Set.of("libmemcached", "memcached-tool"),
            Set.of("Redis", "Hazelcast", "KeyDB"),
            Set.of("PostgreSQL", "MongoDB"),
            "storage"
        ));
        
        TECHNOLOGY_ONTOLOGY.put("Hazelcast", new TechnologyDefinition(
            "Hazelcast", "cache", "distributed_cache", "platform",
            Set.of("JVM"),
            Set.of("Hazelcast Management Center", "Jet", "IMDG"),
            Set.of("Redis Cluster", "Apache Ignite", "Coherence"),
            Set.of("PostgreSQL", "MongoDB"),
            "storage"
        ));
    }

    // Relationship types for semantic understanding
    public enum RelationshipType {
        COMPLEMENTS,        // Works together (Kubernetes + Helm)
        ALTERNATIVE_TO,     // Can replace (Kubernetes ↔ ECS)
        REQUIRES,          // Dependency (Helm → Kubernetes)
        CONFLICTS_WITH,    // Cannot coexist (SQS ↔ Kafka)
        BUILDS_ON_TOP_OF,  // Layered dependency (Istio → Kubernetes)
        USED_WITH,         // Commonly paired (Prometheus + Grafana)
        REPLACES,          // Direct substitution (PostgreSQL → MySQL)
        DEPLOYS_TO         // Deployment target (App → Kubernetes)
    }

    public TechnologyRelationshipAnalysis analyzeTechnologyRelationships(List<String> technologies) {
        TechnologyRelationshipAnalysis analysis = new TechnologyRelationshipAnalysis();
        
        for (String tech : technologies) {
            TechnologyDefinition def = TECHNOLOGY_ONTOLOGY.get(tech);
            if (def != null) {
                analysis.recognizedTechnologies.add(tech);
                analysis.layerDistribution.merge(def.layer, 1, Integer::sum);
                analysis.categoryDistribution.merge(def.category, 1, Integer::sum);
            } else {
                analysis.unrecognizedTechnologies.add(tech);
            }
        }
        
        // Analyze relationships
        analysis.relationships = findRelationships(technologies);
        analysis.conflicts = findConflicts(technologies);
        analysis.missingDependencies = findMissingDependencies(technologies);
        analysis.complementSuggestions = findComplementSuggestions(technologies);
        
        // Calculate coherence score
        analysis.coherenceScore = calculateCoherenceScore(analysis);
        
        log.info("[ontology] Analyzed {} technologies - Coherence: {:.2f}, Conflicts: {}, Missing deps: {}", 
                technologies.size(), analysis.coherenceScore, analysis.conflicts.size(), 
                analysis.missingDependencies.size());
        
        return analysis;
    }

    public boolean isValidReplacement(String original, String replacement) {
        TechnologyDefinition originalDef = TECHNOLOGY_ONTOLOGY.get(original);
        TechnologyDefinition replacementDef = TECHNOLOGY_ONTOLOGY.get(replacement);
        
        if (originalDef == null || replacementDef == null) {
            log.warn("[ontology] Unknown technology in replacement: {} → {}", original, replacement);
            return false; // Conservative: reject unknown technologies
        }
        
        // Rule 1: Must be same role (platform, tool, service, library)
        if (!originalDef.role.equals(replacementDef.role)) {
            log.debug("[ontology] Role mismatch: {} ({}) → {} ({})", 
                    original, originalDef.role, replacement, replacementDef.role);
            return false;
        }
        
        // Rule 2: Must be same layer
        if (!originalDef.layer.equals(replacementDef.layer)) {
            log.debug("[ontology] Layer mismatch: {} ({}) → {} ({})", 
                    original, originalDef.layer, replacement, replacementDef.layer);
            return false;
        }
        
        // Rule 3: Must be in alternatives list OR same category
        boolean isAlternative = originalDef.alternatives.contains(replacement) || 
                               replacementDef.alternatives.contains(original);
        boolean isSameCategory = originalDef.category.equals(replacementDef.category);
        
        if (!isAlternative && !isSameCategory) {
            log.debug("[ontology] Not alternative and different category: {} → {}", original, replacement);
            return false;
        }
        
        // Rule 4: Must not conflict
        if (originalDef.conflicts.contains(replacement) || replacementDef.conflicts.contains(original)) {
            log.debug("[ontology] Conflicting technologies: {} ↔ {}", original, replacement);
            return false;
        }
        
        log.debug("[ontology] Valid replacement: {} → {} (same role: {}, layer: {})", 
                original, replacement, originalDef.role, originalDef.layer);
        return true;
    }

    public List<String> findValidAlternatives(String technology) {
        TechnologyDefinition def = TECHNOLOGY_ONTOLOGY.get(technology);
        if (def == null) {
            return List.of();
        }
        
        return def.alternatives.stream()
                .filter(alt -> isValidReplacement(technology, alt))
                .collect(Collectors.toList());
    }

    public List<String> findComplementaryTechnologies(String technology) {
        TechnologyDefinition def = TECHNOLOGY_ONTOLOGY.get(technology);
        if (def == null) {
            return List.of();
        }
        
        return new ArrayList<>(def.complements);
    }

    public String findBestReplacement(String technology, String architectureProfile, List<String> existingTechnologies) {
        List<String> alternatives = findValidAlternatives(technology);
        if (alternatives.isEmpty()) {
            return technology; // No valid alternatives
        }
        
        // Score alternatives based on architecture profile and existing technologies
        String bestAlternative = alternatives.stream()
                .max((a, b) -> Double.compare(
                    scoreReplacementFit(a, architectureProfile, existingTechnologies),
                    scoreReplacementFit(b, architectureProfile, existingTechnologies)
                ))
                .orElse(technology);
        
        log.info("[ontology] Best replacement for {}: {} (profile: {})", 
                technology, bestAlternative, architectureProfile);
        
        return bestAlternative;
    }

    private double scoreReplacementFit(String technology, String architectureProfile, List<String> existingTechnologies) {
        TechnologyDefinition def = TECHNOLOGY_ONTOLOGY.get(technology);
        if (def == null) return 0.0;
        
        double score = 0.0;
        
        // Architecture profile alignment
        switch (architectureProfile) {
            case "CLOUD_NATIVE_KUBERNETES" -> {
                if (technology.contains("Kubernetes") || def.dependencies.contains("Kubernetes")) score += 0.4;
                if (def.layer.equals("infrastructure") || def.layer.equals("orchestration")) score += 0.2;
            }
            case "AWS_ENTERPRISE" -> {
                if (def.dependencies.contains("AWS") || technology.startsWith("AWS") || 
                    Set.of("ECS", "Lambda", "SQS", "RDS", "CloudWatch").contains(technology)) score += 0.4;
            }
            case "AI_INFERENCE_PLATFORM" -> {
                if (def.layer.equals("ai_inference") || def.category.contains("ai_")) score += 0.4;
            }
        }
        
        // Existing technology synergy
        for (String existing : existingTechnologies) {
            TechnologyDefinition existingDef = TECHNOLOGY_ONTOLOGY.get(existing);
            if (existingDef != null) {
                if (existingDef.complements.contains(technology)) score += 0.1;
                if (def.complements.contains(existing)) score += 0.1;
                if (existingDef.dependencies.contains(technology)) score += 0.2;
            }
        }
        
        return score;
    }

    private List<TechnologyRelationship> findRelationships(List<String> technologies) {
        List<TechnologyRelationship> relationships = new ArrayList<>();
        
        for (String tech1 : technologies) {
            TechnologyDefinition def1 = TECHNOLOGY_ONTOLOGY.get(tech1);
            if (def1 == null) continue;
            
            for (String tech2 : technologies) {
                if (tech1.equals(tech2)) continue;
                
                TechnologyDefinition def2 = TECHNOLOGY_ONTOLOGY.get(tech2);
                if (def2 == null) continue;
                
                // Find relationship type
                RelationshipType relationship = determineRelationshipType(def1, def2, tech1, tech2);
                if (relationship != null) {
                    relationships.add(new TechnologyRelationship(tech1, tech2, relationship));
                }
            }
        }
        
        return relationships;
    }

    private RelationshipType determineRelationshipType(TechnologyDefinition def1, TechnologyDefinition def2, 
                                                      String tech1, String tech2) {
        if (def1.complements.contains(tech2)) return RelationshipType.COMPLEMENTS;
        if (def1.alternatives.contains(tech2)) return RelationshipType.ALTERNATIVE_TO;
        if (def1.dependencies.contains(tech2)) return RelationshipType.REQUIRES;
        if (def2.dependencies.contains(tech1)) return RelationshipType.BUILDS_ON_TOP_OF;
        if (def1.conflicts.contains(tech2)) return RelationshipType.CONFLICTS_WITH;
        
        // Infer common usage patterns
        if (def1.layer.equals(def2.layer) && !def1.category.equals(def2.category)) {
            return RelationshipType.USED_WITH;
        }
        
        return null;
    }

    private List<String> findConflicts(List<String> technologies) {
        List<String> conflicts = new ArrayList<>();
        
        for (String tech1 : technologies) {
            TechnologyDefinition def1 = TECHNOLOGY_ONTOLOGY.get(tech1);
            if (def1 == null) continue;
            
            for (String tech2 : technologies) {
                if (tech1.equals(tech2)) continue;
                
                if (def1.conflicts.contains(tech2)) {
                    conflicts.add(tech1 + " ↔ " + tech2);
                }
            }
        }
        
        return conflicts;
    }

    private List<String> findMissingDependencies(List<String> technologies) {
        List<String> missing = new ArrayList<>();
        Set<String> techSet = new HashSet<>(technologies);
        
        for (String tech : technologies) {
            TechnologyDefinition def = TECHNOLOGY_ONTOLOGY.get(tech);
            if (def == null) continue;
            
            for (String dependency : def.dependencies) {
                if (!techSet.contains(dependency)) {
                    missing.add(tech + " requires " + dependency);
                }
            }
        }
        
        return missing;
    }

    private List<String> findComplementSuggestions(List<String> technologies) {
        Set<String> suggestions = new HashSet<>();
        Set<String> techSet = new HashSet<>(technologies);
        
        for (String tech : technologies) {
            TechnologyDefinition def = TECHNOLOGY_ONTOLOGY.get(tech);
            if (def == null) continue;
            
            for (String complement : def.complements) {
                if (!techSet.contains(complement)) {
                    suggestions.add(complement);
                }
            }
        }
        
        return new ArrayList<>(suggestions);
    }

    private double calculateCoherenceScore(TechnologyRelationshipAnalysis analysis) {
        if (analysis.recognizedTechnologies.isEmpty()) return 0.0;
        
        double score = 0.5; // Start with neutral score
        int totalTech = analysis.recognizedTechnologies.size();
        
        // Penalize conflicts heavily
        score -= analysis.conflicts.size() * 0.3;
        
        // Penalize missing dependencies moderately
        score -= analysis.missingDependencies.size() * 0.15;
        
        // Reward layer diversity (but not too much)
        int layers = analysis.layerDistribution.size();
        if (layers >= 2 && layers <= 4) score += 0.15;
        
        // Reward complementary relationships significantly
        long complementCount = analysis.relationships.stream()
                .mapToLong(r -> r.type == RelationshipType.COMPLEMENTS ? 1 : 0)
                .sum();
        score += Math.min(0.4, complementCount * 0.08);
        
        // Reward alternative relationships (shows good technology selection)
        long alternativeCount = analysis.relationships.stream()
                .mapToLong(r -> r.type == RelationshipType.ALTERNATIVE_TO ? 1 : 0)
                .sum();
        score += Math.min(0.2, alternativeCount * 0.05);
        
        // Bonus for technology ecosystems (e.g., Kubernetes + Helm + Istio)
        if (complementCount >= 3 && analysis.conflicts.isEmpty()) {
            score += 0.2; // Ecosystem bonus
        }
        
        // Normalize to 0-1 range
        return Math.max(0.0, Math.min(1.0, score));
    }

    // Data classes
    public static class TechnologyDefinition {
        public final String name;
        public final String category;
        public final String subcategory;
        public final String role;
        public final Set<String> dependencies;
        public final Set<String> complements;
        public final Set<String> alternatives;
        public final Set<String> conflicts;
        public final String layer;

        public TechnologyDefinition(String name, String category, String subcategory, String role,
                                  Set<String> dependencies, Set<String> complements, 
                                  Set<String> alternatives, Set<String> conflicts, String layer) {
            this.name = name;
            this.category = category;
            this.subcategory = subcategory;
            this.role = role;
            this.dependencies = dependencies;
            this.complements = complements;
            this.alternatives = alternatives;
            this.conflicts = conflicts;
            this.layer = layer;
        }
    }

    public static class TechnologyRelationship {
        public final String from;
        public final String to;
        public final RelationshipType type;

        public TechnologyRelationship(String from, String to, RelationshipType type) {
            this.from = from;
            this.to = to;
            this.type = type;
        }
    }

    public static class TechnologyRelationshipAnalysis {
        public Set<String> recognizedTechnologies = new HashSet<>();
        public Set<String> unrecognizedTechnologies = new HashSet<>();
        public Map<String, Integer> layerDistribution = new HashMap<>();
        public Map<String, Integer> categoryDistribution = new HashMap<>();
        public List<TechnologyRelationship> relationships = new ArrayList<>();
        public List<String> conflicts = new ArrayList<>();
        public List<String> missingDependencies = new ArrayList<>();
        public List<String> complementSuggestions = new ArrayList<>();
        public double coherenceScore = 0.0;
    }
}