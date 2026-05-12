package com.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class StackConsistencyEngine {

    private static final Logger log = LoggerFactory.getLogger(StackConsistencyEngine.class);

    private final TechnologyOntologyEngine ontologyEngine;
    private final DependencyGraphEngine dependencyGraphEngine;

    public StackConsistencyEngine(TechnologyOntologyEngine ontologyEngine,
                                  DependencyGraphEngine dependencyGraphEngine) {
        this.ontologyEngine = ontologyEngine;
        this.dependencyGraphEngine = dependencyGraphEngine;
    }

    // Architecture profiles with consistent technology stacks
    private static final Map<String, ArchitectureProfile> ARCHITECTURE_PROFILES;
    static {
        ARCHITECTURE_PROFILES = new HashMap<>();
        
        ARCHITECTURE_PROFILES.put("CLOUD_NATIVE_KUBERNETES", new ArchitectureProfile(
            "CLOUD_NATIVE_KUBERNETES",
            "Cloud-native platform built on Kubernetes ecosystem",
            Map.of(
                "ORCHESTRATION", List.of("Kubernetes", "Helm", "Kustomize"),
                "SERVICE_MESH", List.of("Istio", "Linkerd", "Envoy Proxy"),
                "MONITORING", List.of("Prometheus", "Grafana", "Jaeger", "OpenTelemetry"),
                "LOGGING", List.of("Loki", "Fluentd", "Elasticsearch"),
                "GITOPS", List.of("ArgoCD", "Flux CD", "Tekton"),
                "NETWORKING", List.of("Envoy Proxy", "Istio Gateway", "NGINX Ingress"),
                "COMMUNICATION", List.of("gRPC", "HTTP/2", "GraphQL"),
                "STORAGE", List.of("Persistent Volumes", "CSI drivers", "Rook Ceph"),
                "SECURITY", List.of("OPA Gatekeeper", "Falco", "cert-manager")
            ),
            List.of("microservices", "cloud-native", "containerization", "service mesh", "observability")
        ));

        ARCHITECTURE_PROFILES.put("AWS_ENTERPRISE", new ArchitectureProfile(
            "AWS_ENTERPRISE",
            "Enterprise-grade AWS cloud architecture",
            Map.of(
                "COMPUTE", List.of("ECS", "EKS", "Lambda", "Fargate"),
                "MESSAGING", List.of("SQS", "SNS", "EventBridge", "Kinesis"),
                "DATABASE", List.of("RDS", "DynamoDB", "Aurora", "DocumentDB"),
                "MONITORING", List.of("CloudWatch", "X-Ray", "AWS Config"),
                "NETWORKING", List.of("VPC", "ALB", "NLB", "API Gateway"),
                "SECURITY", List.of("IAM", "Cognito", "Secrets Manager", "KMS"),
                "STORAGE", List.of("S3", "EFS", "EBS", "FSx"),
                "DEPLOYMENT", List.of("CodePipeline", "CodeBuild", "CodeDeploy", "CloudFormation"),
                "CDN", List.of("CloudFront", "Route 53", "WAF")
            ),
            List.of("enterprise", "aws", "cloud", "managed services", "serverless")
        ));

        ARCHITECTURE_PROFILES.put("HIGH_SCALE_DISTRIBUTED_SYSTEMS", new ArchitectureProfile(
            "HIGH_SCALE_DISTRIBUTED_SYSTEMS",
            "High-throughput distributed systems architecture",
            Map.of(
                "MESSAGING", List.of("Apache Kafka", "Apache Pulsar", "NATS Streaming"),
                "DATABASE", List.of("Apache Cassandra", "CockroachDB", "ScyllaDB"),
                "CACHING", List.of("Redis Cluster", "Hazelcast", "Apache Ignite"),
                "LOAD_BALANCING", List.of("Envoy Proxy", "HAProxy", "NGINX Plus"),
                "COMMUNICATION", List.of("gRPC", "Apache Thrift", "Protocol Buffers"),
                "COORDINATION", List.of("Apache Zookeeper", "etcd", "Consul"),
                "PATTERNS", List.of("CQRS", "Event Sourcing", "Saga Pattern", "Circuit Breaker"),
                "CONSENSUS", List.of("Raft", "Paxos", "PBFT"),
                "MONITORING", List.of("Prometheus", "Grafana", "Jaeger", "Zipkin"),
                "SEARCH", List.of("Elasticsearch", "Apache Solr", "OpenSearch")
            ),
            List.of("distributed", "high-scale", "event-driven", "microservices", "performance")
        ));

        ARCHITECTURE_PROFILES.put("AI_INFERENCE_PLATFORM", new ArchitectureProfile(
            "AI_INFERENCE_PLATFORM",
            "Production AI/ML inference and serving platform",
            Map.of(
                "INFERENCE_SERVING", List.of("vLLM", "Triton Inference Server", "TorchServe", "Ray Serve"),
                "OPTIMIZATION", List.of("TensorRT-LLM", "ONNX Runtime", "OpenVINO", "TensorRT"),
                "TRAINING", List.of("DeepSpeed", "FairScale FSDP", "Horovod", "PyTorch DDP"),
                "FINE_TUNING", List.of("QLoRA", "LoRA", "AdaLoRA", "PEFT"),
                "VECTOR_DB", List.of("FAISS", "Weaviate", "Pinecone", "Chroma"),
                "ORCHESTRATION", List.of("Kubernetes", "Kubeflow", "MLflow", "Ray"),
                "MONITORING", List.of("Prometheus", "Grafana", "Weights & Biases", "Neptune"),
                "STORAGE", List.of("S3", "MinIO", "Ceph", "HDFS"),
                "COMMUNICATION", List.of("gRPC", "HTTP/2", "WebSocket", "Server-Sent Events"),
                "ACCELERATION", List.of("CUDA", "ROCm", "Intel MKL", "Apple Metal")
            ),
            List.of("ai", "ml", "inference", "gpu", "optimization", "serving")
        ));

        ARCHITECTURE_PROFILES.put("FRONTEND_PLATFORM", new ArchitectureProfile(
            "FRONTEND_PLATFORM",
            "Modern frontend development and deployment platform",
            Map.of(
                "FRAMEWORK", List.of("React 18", "Next.js 14", "Remix", "SvelteKit"),
                "BUILD_TOOLS", List.of("Vite", "Turbopack", "esbuild", "SWC"),
                "STATE_MANAGEMENT", List.of("Redux Toolkit", "Zustand", "Jotai", "React Query"),
                "STYLING", List.of("Tailwind CSS", "Styled Components", "CSS Modules", "Emotion"),
                "TESTING", List.of("Vitest", "Jest", "React Testing Library", "Playwright"),
                "DEPLOYMENT", List.of("Vercel", "Netlify", "AWS CloudFront", "Cloudflare Pages"),
                "MONITORING", List.of("Sentry", "LogRocket", "Datadog RUM", "New Relic Browser"),
                "BUNDLING", List.of("Webpack 5", "Rollup", "Parcel", "Turbopack"),
                "PERFORMANCE", List.of("React Server Components", "Suspense", "Code Splitting", "Lazy Loading")
            ),
            List.of("frontend", "react", "performance", "modern", "ssr")
        ));

        ARCHITECTURE_PROFILES.put("STARTUP_MVP", new ArchitectureProfile(
            "STARTUP_MVP",
            "Rapid development stack for MVP and early-stage products",
            Map.of(
                "BACKEND", List.of("Next.js API Routes", "Supabase", "Firebase", "Railway"),
                "DATABASE", List.of("PostgreSQL", "Supabase", "PlanetScale", "MongoDB Atlas"),
                "AUTHENTICATION", List.of("NextAuth.js", "Supabase Auth", "Firebase Auth", "Auth0"),
                "DEPLOYMENT", List.of("Vercel", "Railway", "Render", "Fly.io"),
                "MONITORING", List.of("Sentry", "LogRocket", "Posthog", "Mixpanel"),
                "PAYMENTS", List.of("Stripe", "Paddle", "LemonSqueezy", "PayPal"),
                "EMAIL", List.of("Resend", "SendGrid", "Mailgun", "Postmark"),
                "STORAGE", List.of("Cloudinary", "AWS S3", "Supabase Storage", "UploadThing"),
                "FRONTEND", List.of("Next.js", "React", "Tailwind CSS", "shadcn/ui")
            ),
            List.of("startup", "mvp", "rapid", "simple", "cost-effective")
        ));
    }

    // Technology compatibility matrix
    private static final Map<String, Set<String>> INCOMPATIBLE_COMBINATIONS;
    static {
        INCOMPATIBLE_COMBINATIONS = new HashMap<>();
        INCOMPATIBLE_COMBINATIONS.put("SQS", Set.of("Kafka", "Pulsar", "RabbitMQ"));
        INCOMPATIBLE_COMBINATIONS.put("CloudWatch", Set.of("Prometheus", "Grafana", "DataDog"));
        INCOMPATIBLE_COMBINATIONS.put("ECS", Set.of("Kubernetes", "Docker Swarm"));
        INCOMPATIBLE_COMBINATIONS.put("DynamoDB", Set.of("PostgreSQL", "MySQL", "Cassandra"));
        INCOMPATIBLE_COMBINATIONS.put("Lambda", Set.of("Docker", "Kubernetes pods"));
        INCOMPATIBLE_COMBINATIONS.put("API Gateway", Set.of("Envoy", "Istio Gateway", "NGINX Ingress"));
        INCOMPATIBLE_COMBINATIONS.put("Firebase", Set.of("AWS", "Supabase", "custom backend"));
        INCOMPATIBLE_COMBINATIONS.put("Vercel", Set.of("AWS ECS", "Kubernetes", "Docker deployment"));
    }

    // Technology progression chains
    private static final Map<String, List<String>> TECHNOLOGY_CHAINS;
    static {
        TECHNOLOGY_CHAINS = new HashMap<>();
        TECHNOLOGY_CHAINS.put("KAFKA_ECOSYSTEM", List.of(
            "Apache Kafka", "Schema Registry", "Kafka Connect", "KSQL", "Kafka Streams", "Consumer Groups", "Dead Letter Queues"
        ));
        TECHNOLOGY_CHAINS.put("KUBERNETES_ECOSYSTEM", List.of(
            "Kubernetes", "Helm", "ArgoCD", "Prometheus", "Grafana", "Istio", "Jaeger", "Fluentd"
        ));
        TECHNOLOGY_CHAINS.put("RAG_PIPELINE", List.of(
            "Document Processing", "Embedding Generation", "Vector Database", "Semantic Chunking", "Retrieval", "Reranking", "LLM Generation"
        ));
        TECHNOLOGY_CHAINS.put("LLM_INFERENCE", List.of(
            "Model Loading", "Quantization", "vLLM Server", "TensorRT Optimization", "Flash Attention", "KV Cache", "Batching", "Monitoring"
        ));
        TECHNOLOGY_CHAINS.put("MICROSERVICES_OBSERVABILITY", List.of(
            "Service Deployment", "Health Checks", "Metrics Collection", "Distributed Tracing", "Log Aggregation", "Alerting", "Dashboards"
        ));
    }

    public ArchitectureAnalysis analyzeArchitecturalIntent(String userInput, String domain) {
        ArchitectureAnalysis analysis = new ArchitectureAnalysis();
        
        String lowerInput = userInput.toLowerCase();
        
        // Detect architecture style based on keywords
        String detectedProfile = detectArchitectureProfile(lowerInput, domain);
        analysis.recommendedProfile = detectedProfile;
        analysis.profileConfidence = calculateProfileConfidence(lowerInput, detectedProfile);
        
        // Get the architecture profile
        ArchitectureProfile profile = ARCHITECTURE_PROFILES.get(detectedProfile);
        if (profile != null) {
            analysis.consistentTechnologies = profile.getAllTechnologies();
            analysis.technologyCategories = profile.getTechnologyCategories();
            analysis.architectureDescription = profile.getDescription();
        }
        
        // Detect technology progression needs
        analysis.progressionChains = detectProgressionChains(lowerInput);
        
        log.info("[stack-consistency] Detected profile: {} (confidence: {:.2f}) for domain: {}", 
                detectedProfile, analysis.profileConfidence, domain);
        
        return analysis;
    }

    public ConsistencyValidation validateTechnologyStack(List<String> technologies, String architectureProfile) {
        ConsistencyValidation validation = new ConsistencyValidation();
        
        ArchitectureProfile profile = ARCHITECTURE_PROFILES.get(architectureProfile);
        if (profile == null) {
            validation.isConsistent = false;
            validation.issues.add("Unknown architecture profile: " + architectureProfile);
            return validation;
        }
        
        Set<String> profileTechnologies = profile.getAllTechnologies();
        List<String> inconsistentTechs = new ArrayList<>();
        List<String> conflictingPairs = new ArrayList<>();
        
        // Check for technologies outside the profile
        for (String tech : technologies) {
            if (!isCompatibleWithProfile(tech, profileTechnologies)) {
                inconsistentTechs.add(tech);
            }
        }
        
        // Check for incompatible combinations
        for (String tech1 : technologies) {
            Set<String> incompatible = INCOMPATIBLE_COMBINATIONS.get(tech1);
            if (incompatible != null) {
                for (String tech2 : technologies) {
                    if (incompatible.contains(tech2)) {
                        conflictingPairs.add(tech1 + " + " + tech2);
                    }
                }
            }
        }
        
        // Calculate consistency score
        double consistencyScore = calculateConsistencyScore(technologies, profileTechnologies, conflictingPairs.size());
        
        validation.consistencyScore = consistencyScore;
        validation.isConsistent = consistencyScore > 0.7 && conflictingPairs.isEmpty();
        validation.inconsistentTechnologies = inconsistentTechs;
        validation.conflictingPairs = conflictingPairs;
        
        // Generate recommendations
        if (!validation.isConsistent) {
            validation.recommendations = generateConsistencyRecommendations(
                inconsistentTechs, conflictingPairs, profile);
        }
        
        log.info("[stack-consistency] Validation - Profile: {} | Score: {:.2f} | Consistent: {} | Conflicts: {}", 
                architectureProfile, consistencyScore, validation.isConsistent, conflictingPairs.size());
        
        return validation;
    }

    public List<String> generateConsistentTechnologyStack(String architectureProfile, String category, int count) {
        ArchitectureProfile profile = ARCHITECTURE_PROFILES.get(architectureProfile);
        if (profile == null) {
            return List.of();
        }
        
        List<String> categoryTechnologies = profile.getTechnologyCategories().get(category);
        if (categoryTechnologies == null || categoryTechnologies.isEmpty()) {
            // Fallback to any technologies from the profile
            List<String> allTechs = new ArrayList<>(profile.getAllTechnologies());
            Collections.shuffle(allTechs);
            return allTechs.subList(0, Math.min(count, allTechs.size()));
        }
        
        // Return up to 'count' technologies from the category
        return categoryTechnologies.subList(0, Math.min(count, categoryTechnologies.size()));
    }

    public String enhanceRoadmapWithArchitecturalConsistency(String roadmapContent, String architectureProfile) {
        ArchitectureProfile profile = ARCHITECTURE_PROFILES.get(architectureProfile);
        if (profile == null) {
            return roadmapContent;
        }

        String enhanced = roadmapContent;

        // Extract technologies currently mentioned
        List<String> mentionedTechnologies = extractTechnologiesFromContent(enhanced);

        // PHASE 3E: Dependency graph analysis — detect orphans and incompatible combos
        DependencyGraphEngine.DependencyGraphAnalysis depAnalysis =
                dependencyGraphEngine.analyzeDependencyGraph(mentionedTechnologies);

        log.info("[stack-consistency] Dependency analysis — integrity: {:.2f}, orphans: {}, incompatible: {}",
                depAnalysis.architectureIntegrityScore,
                depAnalysis.orphanTechnologies.size(),
                depAnalysis.incompatibleCombinations.size());

        // PHASE 3E: Resolve incompatible combinations via cascading propagation
        for (String combo : new ArrayList<>(depAnalysis.incompatibleCombinations)) {
            String[] parts = combo.split(" ↔ ");
            if (parts.length != 2) continue;

            String tech1 = parts[0].trim();
            String tech2 = parts[1].trim();

            boolean tech1InProfile = isCompatibleWithProfile(tech1, profile.getAllTechnologies());
            boolean tech2InProfile = isCompatibleWithProfile(tech2, profile.getAllTechnologies());

            // Keep the profile-compatible one; propagate-replace the other
            String toReplace = tech1InProfile ? tech2 : tech1;
            String anchor    = tech1InProfile ? tech1 : tech2;

            // Simulate the replacement to get cascading changes
            DependencyGraphEngine.ReplacementSimulation sim =
                    dependencyGraphEngine.simulateReplacement(toReplace, anchor, mentionedTechnologies);

            if (sim.isValid || !sim.cascadingReplacements.isEmpty()) {
                // Apply cascading replacements in content
                for (Map.Entry<String, String> cr : sim.cascadingReplacements.entrySet()) {
                    enhanced = enhanced.replaceAll("(?i)" + Pattern.quote(cr.getKey()), cr.getValue());
                    log.info("[stack-consistency] Cascading replacement: {} → {}", cr.getKey(), cr.getValue());
                }
                // Remove technologies that became incompatible after the swap
                for (String removed : sim.removedIncompatibleTechnologies) {
                    enhanced = enhanced.replaceAll("(?i)\\b" + Pattern.quote(removed) + "\\b", "");
                    log.info("[stack-consistency] Removed incompatible (cascade): {}", removed);
                }
                // Apply the primary replacement
                String profileReplacement = findCompatibleReplacement(toReplace, profile);
                if (!profileReplacement.equals(toReplace)) {
                    enhanced = enhanced.replaceAll("(?i)" + Pattern.quote(toReplace), profileReplacement);
                    log.info("[stack-consistency] Primary replacement: {} → {}", toReplace, profileReplacement);
                }
                // Refresh mentioned list after changes
                mentionedTechnologies = extractTechnologiesFromContent(enhanced);
            } else {
                log.warn("[stack-consistency] Skipping incompatible pair {} ↔ {} — simulation invalid", tech1, tech2);
            }
        }

        // PHASE 3E: Remove orphan technologies (hard deps missing after cascading)
        DependencyGraphEngine.DependencyGraphAnalysis postCascadeAnalysis =
                dependencyGraphEngine.analyzeDependencyGraph(mentionedTechnologies);

        for (String orphan : postCascadeAnalysis.orphanTechnologies) {
            // Only remove if it's not a core profile technology
            if (!isCompatibleWithProfile(orphan, profile.getAllTechnologies())) {
                enhanced = enhanced.replaceAll("(?i)\\b" + Pattern.quote(orphan) + "\\b", "");
                log.info("[stack-consistency] Removed orphan technology: {}", orphan);
            }
        }

        // PHASE 3D: Relationship-level conflict resolution (ontology layer)
        mentionedTechnologies = extractTechnologiesFromContent(enhanced);
        TechnologyOntologyEngine.TechnologyRelationshipAnalysis relationshipAnalysis =
                ontologyEngine.analyzeTechnologyRelationships(mentionedTechnologies);

        for (String conflict : relationshipAnalysis.conflicts) {
            String[] conflictPair = conflict.split(" ↔ ");
            if (conflictPair.length != 2) continue;

            String tech1 = conflictPair[0].trim();
            String tech2 = conflictPair[1].trim();

            boolean tech1Compatible = isCompatibleWithProfile(tech1, profile.getAllTechnologies());
            boolean tech2Compatible = isCompatibleWithProfile(tech2, profile.getAllTechnologies());

            if (tech1Compatible && !tech2Compatible) {
                String replacement = findCompatibleReplacement(tech2, profile);
                if (!replacement.equals(tech2)) {
                    enhanced = enhanced.replaceAll("(?i)" + Pattern.quote(tech2), replacement);
                    log.info("[stack-consistency] Ontology conflict resolved: kept {}, replaced {} → {}",
                            tech1, tech2, replacement);
                }
            } else if (tech2Compatible && !tech1Compatible) {
                String replacement = findCompatibleReplacement(tech1, profile);
                if (!replacement.equals(tech1)) {
                    enhanced = enhanced.replaceAll("(?i)" + Pattern.quote(tech1), replacement);
                    log.info("[stack-consistency] Ontology conflict resolved: kept {}, replaced {} → {}",
                            tech2, tech1, replacement);
                }
            }
        }

        // PHASE 3D: Missing-dependency handling (only replace if not profile-compatible)
        for (String missingDep : relationshipAnalysis.missingDependencies) {
            if (!missingDep.contains(" requires ")) continue;
            String[] depParts = missingDep.split(" requires ");
            if (depParts.length != 2) continue;

            String technology       = depParts[0].trim();
            String requiredDep      = depParts[1].trim();

            if (isCompatibleWithProfile(requiredDep, profile.getAllTechnologies())) {
                log.info("[stack-consistency] Dependency suggestion: {} requires {} (consider adding)", technology, requiredDep);
            } else if (!isCompatibleWithProfile(technology, profile.getAllTechnologies())) {
                String replacement = findCompatibleReplacement(technology, profile);
                if (!replacement.equals(technology)) {
                    enhanced = enhanced.replaceAll("(?i)" + Pattern.quote(technology), replacement);
                    log.info("[stack-consistency] Dependency issue resolved: {} → {} (avoids {} dep)",
                            technology, replacement, requiredDep);
                }
            } else {
                log.info("[stack-consistency] Keeping {} despite {} dependency (profile-compatible)", technology, requiredDep);
            }
        }

        return enhanced;
    }

    private String detectArchitectureProfile(String input, String domain) {
        Map<String, Double> profileScores = new HashMap<>();
        
        // Initialize all profiles with base scores
        for (String profileName : ARCHITECTURE_PROFILES.keySet()) {
            profileScores.put(profileName, 0.0);
        }
        
        // Domain-based initial scoring
        switch (domain.toUpperCase()) {
            case "AI_ENGINEERING" -> profileScores.put("AI_INFERENCE_PLATFORM", 0.3);
            case "DEVOPS", "CLOUD_ENGINEERING" -> {
                profileScores.put("CLOUD_NATIVE_KUBERNETES", 0.2);
                profileScores.put("AWS_ENTERPRISE", 0.2);
            }
            case "SYSTEM_DESIGN" -> profileScores.put("HIGH_SCALE_DISTRIBUTED_SYSTEMS", 0.3);
            case "REACT_FRONTEND" -> profileScores.put("FRONTEND_PLATFORM", 0.3);
            case "STARTUP", "BUSINESS" -> profileScores.put("STARTUP_MVP", 0.3);
        }
        
        // Keyword-based scoring
        for (Map.Entry<String, ArchitectureProfile> entry : ARCHITECTURE_PROFILES.entrySet()) {
            String profileName = entry.getKey();
            ArchitectureProfile profile = entry.getValue();
            
            double keywordScore = 0.0;
            for (String keyword : profile.getKeywords()) {
                if (input.contains(keyword)) {
                    keywordScore += 0.1;
                }
            }
            
            // Technology mention scoring
            double techScore = 0.0;
            for (String tech : profile.getAllTechnologies()) {
                if (input.toLowerCase().contains(tech.toLowerCase())) {
                    techScore += 0.15;
                }
            }
            
            profileScores.put(profileName, profileScores.get(profileName) + keywordScore + techScore);
        }
        
        // Return the highest scoring profile
        return profileScores.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("CLOUD_NATIVE_KUBERNETES"); // Default fallback
    }

    private double calculateProfileConfidence(String input, String profileName) {
        ArchitectureProfile profile = ARCHITECTURE_PROFILES.get(profileName);
        if (profile == null) return 0.0;
        
        int matches = 0;
        int totalKeywords = profile.getKeywords().size();
        
        for (String keyword : profile.getKeywords()) {
            if (input.contains(keyword)) {
                matches++;
            }
        }
        
        return (double) matches / totalKeywords;
    }

    private List<String> detectProgressionChains(String input) {
        List<String> detectedChains = new ArrayList<>();
        
        for (Map.Entry<String, List<String>> entry : TECHNOLOGY_CHAINS.entrySet()) {
            String chainName = entry.getKey();
            List<String> chainTechnologies = entry.getValue();
            
            int matches = 0;
            for (String tech : chainTechnologies) {
                if (input.toLowerCase().contains(tech.toLowerCase())) {
                    matches++;
                }
            }
            
            // If we have multiple matches in a chain, include it
            if (matches >= 2) {
                detectedChains.add(chainName);
            }
        }
        
        return detectedChains;
    }

    private boolean isCompatibleWithProfile(String technology, Set<String> profileTechnologies) {
        // Direct match
        if (profileTechnologies.contains(technology)) {
            return true;
        }
        
        // Partial match (case-insensitive)
        return profileTechnologies.stream()
                .anyMatch(profileTech -> 
                    profileTech.toLowerCase().contains(technology.toLowerCase()) ||
                    technology.toLowerCase().contains(profileTech.toLowerCase()));
    }

    private double calculateConsistencyScore(List<String> technologies, Set<String> profileTechnologies, int conflicts) {
        if (technologies.isEmpty()) return 0.0;
        
        int compatibleCount = 0;
        for (String tech : technologies) {
            if (isCompatibleWithProfile(tech, profileTechnologies)) {
                compatibleCount++;
            }
        }
        
        double baseScore = (double) compatibleCount / technologies.size();
        double conflictPenalty = conflicts * 0.2; // 20% penalty per conflict
        
        return Math.max(0.0, baseScore - conflictPenalty);
    }

    private List<String> generateConsistencyRecommendations(List<String> inconsistentTechs, 
                                                           List<String> conflictingPairs, 
                                                           ArchitectureProfile profile) {
        List<String> recommendations = new ArrayList<>();
        
        if (!inconsistentTechs.isEmpty()) {
            recommendations.add("Replace inconsistent technologies: " + String.join(", ", inconsistentTechs));
            recommendations.add("Consider using profile-consistent alternatives from: " + 
                              profile.getAllTechnologies().stream().limit(5).collect(Collectors.joining(", ")));
        }
        
        if (!conflictingPairs.isEmpty()) {
            recommendations.add("Resolve conflicting technology pairs: " + String.join(", ", conflictingPairs));
            recommendations.add("Choose one technology from each conflicting pair to maintain consistency");
        }
        
        return recommendations;
    }

    private String findCompatibleReplacement(String incompatibleTech, ArchitectureProfile profile) {
        // PHASE 3D: Use TechnologyOntologyEngine for relationship-aware replacement
        List<String> validAlternatives = ontologyEngine.findValidAlternatives(incompatibleTech);
        
        if (!validAlternatives.isEmpty()) {
            // Find the best alternative that fits the architecture profile
            String bestAlternative = ontologyEngine.findBestReplacement(
                incompatibleTech, profile.getName(), new ArrayList<>(profile.getAllTechnologies()));
            
            // Validate that the replacement is actually valid
            if (ontologyEngine.isValidReplacement(incompatibleTech, bestAlternative)) {
                log.info("[stack-consistency] Ontology-based replacement: {} → {} (profile: {})", 
                        incompatibleTech, bestAlternative, profile.getName());
                return bestAlternative;
            }
        }
        
        // Fallback: Try to find a replacement in the same category within the profile
        for (Map.Entry<String, List<String>> entry : profile.getTechnologyCategories().entrySet()) {
            List<String> categoryTechs = entry.getValue();
            
            // Check if any technology in this category could be a valid replacement
            for (String candidateReplacement : categoryTechs) {
                if (ontologyEngine.isValidReplacement(incompatibleTech, candidateReplacement)) {
                    log.info("[stack-consistency] Profile-based replacement: {} → {} (category: {})", 
                            incompatibleTech, candidateReplacement, entry.getKey());
                    return candidateReplacement;
                }
            }
        }
        
        // Last resort: Category-specific replacements (legacy fallback)
        String replacement = getCategorySpecificReplacement(incompatibleTech, profile);
        if (replacement != null && ontologyEngine.isValidReplacement(incompatibleTech, replacement)) {
            log.info("[stack-consistency] Category-based replacement: {} → {}", incompatibleTech, replacement);
            return replacement;
        }
        
        // No valid replacement found - return original to avoid semantic errors
        log.warn("[stack-consistency] No valid replacement found for {} in profile {}, keeping original", 
                incompatibleTech, profile.getName());
        return incompatibleTech;
    }
    
    private List<String> extractTechnologiesFromContent(String content) {
        List<String> technologies = new ArrayList<>();
        String lowerContent = content.toLowerCase();
        
        // Extract technologies mentioned in the content by checking against known technologies
        // This is a simple approach - in production, you might want more sophisticated NLP
        
        // Common technology patterns
        String[] commonTechnologies = {
            "Kubernetes", "Docker", "Helm", "Istio", "ArgoCD", "Prometheus", "Grafana",
            "Apache Kafka", "RabbitMQ", "Redis", "PostgreSQL", "MongoDB", "MySQL",
            "React", "Vue.js", "Angular", "Next.js", "Express.js", "Spring Boot",
            "AWS", "GCP", "Azure", "Vercel", "Netlify", "Jenkins", "GitHub Actions",
            "Terraform", "Ansible", "Chef", "Puppet", "Nginx", "HAProxy", "Envoy"
        };
        
        for (String tech : commonTechnologies) {
            if (lowerContent.contains(tech.toLowerCase())) {
                technologies.add(tech);
            }
        }
        
        return technologies;
    }

    private String getCategorySpecificReplacement(String incompatibleTech, ArchitectureProfile profile) {
        String lowerTech = incompatibleTech.toLowerCase();
        
        // Messaging replacements
        if (lowerTech.contains("sqs") || lowerTech.contains("sns")) {
            List<String> messaging = profile.getTechnologyCategories().get("MESSAGING");
            if (messaging != null && !messaging.isEmpty()) {
                return messaging.get(0);
            }
        }
        
        // Monitoring replacements
        if (lowerTech.contains("cloudwatch") || lowerTech.contains("datadog")) {
            List<String> monitoring = profile.getTechnologyCategories().get("MONITORING");
            if (monitoring != null && !monitoring.isEmpty()) {
                return monitoring.get(0);
            }
        }
        
        // Orchestration replacements
        if (lowerTech.contains("ecs") || lowerTech.contains("docker swarm")) {
            List<String> orchestration = profile.getTechnologyCategories().get("ORCHESTRATION");
            if (orchestration != null && !orchestration.isEmpty()) {
                return orchestration.get(0);
            }
        }
        
        // Database replacements
        if (lowerTech.contains("dynamodb") || lowerTech.contains("rds")) {
            List<String> database = profile.getTechnologyCategories().get("DATABASE");
            if (database != null && !database.isEmpty()) {
                return database.get(0);
            }
        }
        
        return null;
    }

    // Data classes
    public static class ArchitectureProfile {
        private final String name;
        private final String description;
        private final Map<String, List<String>> technologyCategories;
        private final List<String> keywords;

        public ArchitectureProfile(String name, String description, 
                                 Map<String, List<String>> technologyCategories, 
                                 List<String> keywords) {
            this.name = name;
            this.description = description;
            this.technologyCategories = technologyCategories;
            this.keywords = keywords;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public Map<String, List<String>> getTechnologyCategories() { return technologyCategories; }
        public List<String> getKeywords() { return keywords; }

        public Set<String> getAllTechnologies() {
            return technologyCategories.values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toSet());
        }
    }

    public static class ArchitectureAnalysis {
        public String recommendedProfile = "";
        public double profileConfidence = 0.0;
        public Set<String> consistentTechnologies = new HashSet<>();
        public Map<String, List<String>> technologyCategories = new HashMap<>();
        public String architectureDescription = "";
        public List<String> progressionChains = new ArrayList<>();
    }

    public static class ConsistencyValidation {
        public boolean isConsistent = true;
        public double consistencyScore = 0.0;
        public List<String> inconsistentTechnologies = new ArrayList<>();
        public List<String> conflictingPairs = new ArrayList<>();
        public List<String> issues = new ArrayList<>();
        public List<String> recommendations = new ArrayList<>();
    }
}