package com.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DependencyGraphEngine {

    private static final Logger log = LoggerFactory.getLogger(DependencyGraphEngine.class);

    // Technology dependency graph with ecosystem relationships
    private static final Map<String, TechnologyNode> DEPENDENCY_GRAPH;
    
    static {
        DEPENDENCY_GRAPH = new HashMap<>();
        
        // ===== KUBERNETES ECOSYSTEM =====
        DEPENDENCY_GRAPH.put("Kubernetes", new TechnologyNode(
            "Kubernetes", "orchestration", "platform",
            Set.of(), // hard dependencies
            Set.of("Docker", "containerd"), // optional dependencies
            Set.of("Helm", "Istio", "ArgoCD", "Prometheus Operator", "Jaeger", "Fluentd"), // ecosystem attachments
            Set.of("Kubernetes"), // compatible orchestration platforms
            Set.of("container-based", "microservices", "cloud-native"), // deployment assumptions
            Set.of("container runtime", "cluster networking", "persistent storage") // infrastructure assumptions
        ));
        
        DEPENDENCY_GRAPH.put("Helm", new TechnologyNode(
            "Helm", "package_management", "tool",
            Set.of("Kubernetes"), // hard dependencies
            Set.of(), // optional dependencies
            Set.of("ArgoCD", "Kustomize"), // ecosystem attachments
            Set.of("Kubernetes"), // compatible orchestration platforms
            Set.of("kubernetes-native", "helm-charts"), // deployment assumptions
            Set.of("kubernetes-api", "tiller-optional") // infrastructure assumptions
        ));
        
        DEPENDENCY_GRAPH.put("Istio", new TechnologyNode(
            "Istio", "service_mesh", "platform",
            Set.of("Kubernetes"), // hard dependencies
            Set.of("Envoy"), // optional dependencies
            Set.of("Jaeger", "Prometheus", "Grafana", "Kiali"), // ecosystem attachments
            Set.of("Kubernetes"), // compatible orchestration platforms
            Set.of("sidecar-proxy", "service-mesh"), // deployment assumptions
            Set.of("kubernetes-api", "envoy-proxy", "mtls") // infrastructure assumptions
        ));
        
        DEPENDENCY_GRAPH.put("ArgoCD", new TechnologyNode(
            "ArgoCD", "gitops", "tool",
            Set.of("Kubernetes"), // hard dependencies — Kubernetes is required
            Set.of("Helm", "Kustomize", "Git"), // optional dependencies
            Set.of("Tekton", "Flux CD"), // ecosystem attachments
            Set.of("Kubernetes", "OpenShift"), // compatible orchestration platforms
            Set.of("gitops", "declarative-config"), // deployment assumptions
            Set.of("kubernetes-api", "git-repository") // infrastructure assumptions
        ));
        
        DEPENDENCY_GRAPH.put("Prometheus Operator", new TechnologyNode(
            "Prometheus Operator", "monitoring", "tool",
            Set.of("Kubernetes", "Prometheus"), // hard dependencies
            Set.of("Grafana"), // optional dependencies
            Set.of("AlertManager", "Jaeger"), // ecosystem attachments
            Set.of("Kubernetes"), // compatible orchestration platforms
            Set.of("operator-pattern", "custom-resources"), // deployment assumptions
            Set.of("kubernetes-api", "prometheus-config") // infrastructure assumptions
        ));
        
        // ===== AWS ECS ECOSYSTEM =====
        DEPENDENCY_GRAPH.put("ECS", new TechnologyNode(
            "ECS", "orchestration", "service",
            Set.of(), // no hard deps — AWS is the cloud provider, treated as ambient
            Set.of("Fargate", "EC2", "AWS"), // optional dependencies
            Set.of("AWS App Mesh", "ECS Service Discovery", "CloudMap", "AWS Load Balancer Controller"),
            Set.of("ECS"),
            Set.of("task-definition", "service-discovery"),
            Set.of("aws-vpc", "iam-roles", "cloudwatch")
        ));
        
        DEPENDENCY_GRAPH.put("AWS App Mesh", new TechnologyNode(
            "AWS App Mesh", "service_mesh", "service",
            Set.of("ECS"), // hard dependency on ECS (or EKS)
            Set.of("Fargate", "EC2"),
            Set.of("X-Ray", "CloudWatch", "ECS Service Discovery"),
            Set.of("ECS", "EKS"),
            Set.of("envoy-proxy", "service-mesh"),
            Set.of("aws-vpc", "service-discovery", "x-ray")
        ));
        
        DEPENDENCY_GRAPH.put("ECS Service Discovery", new TechnologyNode(
            "ECS Service Discovery", "service_discovery", "service",
            Set.of("ECS"), // hard dependency on ECS
            Set.of("Route 53"),
            Set.of("CloudMap", "AWS Load Balancer Controller"),
            Set.of("ECS"),
            Set.of("dns-based", "service-registry"),
            Set.of("aws-vpc", "route53", "cloudmap")
        ));
        
        DEPENDENCY_GRAPH.put("CloudMap", new TechnologyNode(
            "CloudMap", "service_discovery", "service",
            Set.of(), // no hard deps — AWS ambient
            Set.of("Route 53", "VPC", "AWS"),
            Set.of("ECS Service Discovery", "API Gateway"),
            Set.of("ECS", "EKS", "Lambda"),
            Set.of("service-registry", "dns-based"),
            Set.of("aws-vpc", "route53")
        ));
        
        DEPENDENCY_GRAPH.put("AWS Load Balancer Controller", new TechnologyNode(
            "AWS Load Balancer Controller", "load_balancing", "service",
            Set.of("ECS"), // hard dependency on ECS
            Set.of("ALB", "NLB"),
            Set.of("ECS Service Discovery", "CloudMap"),
            Set.of("ECS", "EKS"),
            Set.of("ingress-controller", "load-balancing"),
            Set.of("aws-vpc", "alb", "target-groups")
        ));
        
        // ===== MESSAGING ECOSYSTEMS =====
        DEPENDENCY_GRAPH.put("Apache Kafka", new TechnologyNode(
            "Apache Kafka", "messaging", "platform",
            Set.of("JVM"), // hard dependencies
            Set.of("Zookeeper", "KRaft"), // optional dependencies
            Set.of("Schema Registry", "Kafka Connect", "KSQL", "Kafka Streams"), // ecosystem attachments
            Set.of("Kubernetes", "ECS", "bare-metal"), // compatible orchestration platforms
            Set.of("distributed-streaming", "event-sourcing"), // deployment assumptions
            Set.of("persistent-storage", "network-partitions", "replication") // infrastructure assumptions
        ));
        
        DEPENDENCY_GRAPH.put("Schema Registry", new TechnologyNode(
            "Schema Registry", "schema_management", "service",
            Set.of("Apache Kafka"), // hard dependencies
            Set.of(), // optional dependencies
            Set.of("Kafka Connect", "KSQL"), // ecosystem attachments
            Set.of("Kubernetes", "ECS", "bare-metal"), // compatible orchestration platforms
            Set.of("schema-evolution", "avro-serialization"), // deployment assumptions
            Set.of("kafka-cluster", "persistent-storage") // infrastructure assumptions
        ));
        
        // ===== DATABASE ECOSYSTEMS =====
        DEPENDENCY_GRAPH.put("PostgreSQL", new TechnologyNode(
            "PostgreSQL", "database", "platform",
            Set.of(), // hard dependencies
            Set.of("pgBouncer", "HAProxy"), // optional dependencies
            Set.of("PgBouncer", "pg_stat_statements", "PostGIS", "Citus"), // ecosystem attachments
            Set.of("Kubernetes", "ECS", "bare-metal"), // compatible orchestration platforms
            Set.of("acid-transactions", "relational-model"), // deployment assumptions
            Set.of("persistent-storage", "backup-strategy", "replication") // infrastructure assumptions
        ));
        
        DEPENDENCY_GRAPH.put("PgBouncer", new TechnologyNode(
            "PgBouncer", "connection_pooling", "tool",
            Set.of("PostgreSQL"), // hard dependencies
            Set.of(), // optional dependencies
            Set.of("pgpool-II", "HAProxy"), // ecosystem attachments
            Set.of("Kubernetes", "ECS", "bare-metal"), // compatible orchestration platforms
            Set.of("connection-pooling", "session-pooling"), // deployment assumptions
            Set.of("postgresql-connection", "memory-management") // infrastructure assumptions
        ));
        
        // ===== MONITORING ECOSYSTEMS =====
        DEPENDENCY_GRAPH.put("Prometheus", new TechnologyNode(
            "Prometheus", "monitoring", "platform",
            Set.of(), // hard dependencies
            Set.of("AlertManager", "Node Exporter"), // optional dependencies
            Set.of("Grafana", "AlertManager", "Jaeger", "Node Exporter"), // ecosystem attachments
            Set.of("Kubernetes", "ECS", "bare-metal"), // compatible orchestration platforms
            Set.of("pull-based", "time-series"), // deployment assumptions
            Set.of("persistent-storage", "service-discovery") // infrastructure assumptions
        ));
        
        DEPENDENCY_GRAPH.put("Grafana", new TechnologyNode(
            "Grafana", "visualization", "platform",
            Set.of(), // hard dependencies
            Set.of("Prometheus", "InfluxDB", "Elasticsearch"), // optional dependencies
            Set.of("Prometheus", "AlertManager", "Loki"), // ecosystem attachments
            Set.of("Kubernetes", "ECS", "bare-metal"), // compatible orchestration platforms
            Set.of("dashboard", "alerting"), // deployment assumptions
            Set.of("data-source", "persistent-storage") // infrastructure assumptions
        ));
        
        // ===== INCOMPATIBLE COMBINATIONS =====
        // These will be used for validation
        DEPENDENCY_GRAPH.put("INCOMPATIBLE_ECS_KUBERNETES_TOOLS", new TechnologyNode(
            "INCOMPATIBLE_ECS_KUBERNETES_TOOLS", "validation", "rule",
            Set.of(), Set.of(), Set.of(),
            Set.of(), Set.of(), Set.of()
        ));
    }

    // Ecosystem migration rules for when core platforms change
    private static final Map<String, EcosystemMigration> ECOSYSTEM_MIGRATIONS;
    static {
        ECOSYSTEM_MIGRATIONS = new HashMap<>();
        
        // Kubernetes → ECS migration
        ECOSYSTEM_MIGRATIONS.put("Kubernetes→ECS", new EcosystemMigration(
            "Kubernetes", "ECS",
            Map.of(
                "Helm", "ECS Service Discovery", // Package management → Service discovery
                "Istio", "AWS App Mesh", // Service mesh → AWS service mesh
                "ArgoCD", "AWS CodePipeline", // GitOps → AWS CI/CD
                "Prometheus Operator", "CloudWatch Container Insights", // Monitoring → AWS monitoring
                "Jaeger", "AWS X-Ray", // Tracing → AWS tracing
                "Fluentd", "AWS for Fluent Bit" // Logging → AWS logging
            ),
            Set.of("kubernetes-native", "helm-charts", "operator-pattern"),
            Set.of("task-definition", "service-discovery", "ecs-native")
        ));
        
        // ECS → Kubernetes migration
        ECOSYSTEM_MIGRATIONS.put("ECS→Kubernetes", new EcosystemMigration(
            "ECS", "Kubernetes",
            Map.of(
                "AWS App Mesh", "Istio", // AWS service mesh → Istio
                "ECS Service Discovery", "Kubernetes DNS", // Service discovery → K8s DNS
                "CloudMap", "Kubernetes Service", // Service registry → K8s Service
                "AWS Load Balancer Controller", "NGINX Ingress", // Load balancing → Ingress
                "CloudWatch Container Insights", "Prometheus Operator", // Monitoring → Prometheus
                "AWS X-Ray", "Jaeger" // Tracing → Jaeger
            ),
            Set.of("task-definition", "service-discovery", "ecs-native"),
            Set.of("kubernetes-native", "helm-charts", "operator-pattern")
        ));
        
        // Apache Kafka → SQS migration (messaging paradigm change)
        ECOSYSTEM_MIGRATIONS.put("Apache Kafka→SQS", new EcosystemMigration(
            "Apache Kafka", "SQS",
            Map.of(
                "Schema Registry", "AWS Glue Schema Registry", // Schema management
                "Kafka Connect", "AWS Lambda", // Stream processing
                "KSQL", "Amazon Kinesis Analytics", // Stream analytics
                "Kafka Streams", "AWS Lambda + DynamoDB" // Stream processing
            ),
            Set.of("distributed-streaming", "event-sourcing", "schema-evolution"),
            Set.of("message-queue", "serverless", "managed-service")
        ));
    }

    public DependencyGraphAnalysis analyzeDependencyGraph(List<String> technologies) {
        DependencyGraphAnalysis analysis = new DependencyGraphAnalysis();
        
        // Build dependency graph for current technologies
        Map<String, TechnologyNode> currentGraph = new HashMap<>();
        for (String tech : technologies) {
            TechnologyNode node = DEPENDENCY_GRAPH.get(tech);
            if (node != null) {
                currentGraph.put(tech, node);
                analysis.recognizedTechnologies.add(tech);
            } else {
                analysis.unrecognizedTechnologies.add(tech);
            }
        }
        
        // Analyze dependencies
        analysis.missingHardDependencies = findMissingHardDependencies(currentGraph);
        analysis.orphanTechnologies = findOrphanTechnologies(currentGraph);
        analysis.incompatibleCombinations = findIncompatibleCombinations(currentGraph);
        analysis.ecosystemClusters = identifyEcosystemClusters(currentGraph);
        
        // Calculate architecture integrity score
        analysis.architectureIntegrityScore = calculateArchitectureIntegrityScore(analysis);
        
        log.info("[dependency-graph] Analyzed {} technologies - Integrity: {:.2f}, Orphans: {}, Incompatible: {}", 
                technologies.size(), analysis.architectureIntegrityScore, 
                analysis.orphanTechnologies.size(), analysis.incompatibleCombinations.size());
        
        return analysis;
    }

    public ReplacementSimulation simulateReplacement(String originalTech, String replacementTech, 
                                                   List<String> currentTechnologies) {
        ReplacementSimulation simulation = new ReplacementSimulation();
        simulation.originalTechnology = originalTech;
        simulation.replacementTechnology = replacementTech;
        
        // Get current dependency graph
        DependencyGraphAnalysis beforeAnalysis = analyzeDependencyGraph(currentTechnologies);
        
        // Simulate replacement
        List<String> afterTechnologies = new ArrayList<>(currentTechnologies);
        afterTechnologies.remove(originalTech);
        afterTechnologies.add(replacementTech);
        
        // Apply ecosystem migration if available
        EcosystemMigration migration = ECOSYSTEM_MIGRATIONS.get(originalTech + "→" + replacementTech);
        if (migration != null) {
            simulation.ecosystemMigration = migration;
            
            // Apply ecosystem replacements
            for (Map.Entry<String, String> replacement : migration.technologyReplacements.entrySet()) {
                String oldTech = replacement.getKey();
                String newTech = replacement.getValue();
                
                if (afterTechnologies.contains(oldTech)) {
                    afterTechnologies.remove(oldTech);
                    afterTechnologies.add(newTech);
                    simulation.cascadingReplacements.put(oldTech, newTech);
                }
            }
        }
        
        // Remove incompatible technologies
        TechnologyNode replacementNode = DEPENDENCY_GRAPH.get(replacementTech);
        if (replacementNode != null) {
            List<String> toRemove = new ArrayList<>();
            for (String tech : afterTechnologies) {
                if (isIncompatible(replacementTech, tech)) {
                    toRemove.add(tech);
                    simulation.removedIncompatibleTechnologies.add(tech);
                }
            }
            afterTechnologies.removeAll(toRemove);
        }
        
        // Analyze after replacement
        DependencyGraphAnalysis afterAnalysis = analyzeDependencyGraph(afterTechnologies);
        
        simulation.beforeIntegrityScore = beforeAnalysis.architectureIntegrityScore;
        simulation.afterIntegrityScore = afterAnalysis.architectureIntegrityScore;
        simulation.integrityDelta = afterAnalysis.architectureIntegrityScore - beforeAnalysis.architectureIntegrityScore;
        
        simulation.newOrphanTechnologies = afterAnalysis.orphanTechnologies.stream()
                .filter(tech -> !beforeAnalysis.orphanTechnologies.contains(tech))
                .collect(Collectors.toList());
        
        simulation.newIncompatibleCombinations = afterAnalysis.incompatibleCombinations.stream()
                .filter(combo -> !beforeAnalysis.incompatibleCombinations.contains(combo))
                .collect(Collectors.toList());
        
        simulation.isValid = simulation.integrityDelta >= -0.2 && // Allow small integrity loss
                           simulation.newOrphanTechnologies.size() <= 2 && // Limit new orphans
                           simulation.newIncompatibleCombinations.isEmpty(); // No new incompatibilities
        
        log.info("[dependency-graph] Simulated {} → {} - Integrity: {:.2f} → {:.2f} (Δ{:.2f}), Valid: {}", 
                originalTech, replacementTech, simulation.beforeIntegrityScore, 
                simulation.afterIntegrityScore, simulation.integrityDelta, simulation.isValid);
        
        return simulation;
    }

    public List<String> propagateReplacementWithDependencies(String originalTech, String replacementTech, 
                                                           List<String> currentTechnologies) {
        ReplacementSimulation simulation = simulateReplacement(originalTech, replacementTech, currentTechnologies);
        
        // If a migration rule exists, always apply it — the migration itself is the fix
        boolean hasMigrationRule = ECOSYSTEM_MIGRATIONS.containsKey(originalTech + "→" + replacementTech);
        
        if (!simulation.isValid && !hasMigrationRule) {
            log.warn("[dependency-graph] Replacement {} → {} would create invalid architecture and no migration rule exists, skipping", 
                    originalTech, replacementTech);
            return currentTechnologies; // Return unchanged only when no migration path
        }
        
        List<String> result = new ArrayList<>(currentTechnologies);
        
        // Apply primary replacement
        result.remove(originalTech);
        result.add(replacementTech);
        
        // Apply cascading replacements
        for (Map.Entry<String, String> replacement : simulation.cascadingReplacements.entrySet()) {
            result.remove(replacement.getKey());
            result.add(replacement.getValue());
            log.info("[dependency-graph] Cascading replacement: {} → {}", 
                    replacement.getKey(), replacement.getValue());
        }
        
        // Remove incompatible technologies
        result.removeAll(simulation.removedIncompatibleTechnologies);
        for (String removed : simulation.removedIncompatibleTechnologies) {
            log.info("[dependency-graph] Removed incompatible technology: {}", removed);
        }
        
        log.info("[dependency-graph] Propagated replacement {} → {} with {} cascading changes", 
                originalTech, replacementTech, simulation.cascadingReplacements.size());
        
        return result;
    }

    public ArchitectureIntentAnalysis analyzeArchitectureIntent(List<String> technologies) {
        ArchitectureIntentAnalysis analysis = new ArchitectureIntentAnalysis();
        
        // Detect architectural patterns
        if (technologies.contains("Kubernetes")) {
            analysis.detectedPatterns.add("cloud-native");
            analysis.detectedPatterns.add("microservices");
            analysis.detectedPatterns.add("container-orchestration");
        }
        
        if (technologies.contains("Istio") || technologies.contains("AWS App Mesh")) {
            analysis.detectedPatterns.add("service-mesh");
            analysis.detectedPatterns.add("zero-trust-networking");
        }
        
        if (technologies.contains("ArgoCD") || technologies.contains("Flux CD")) {
            analysis.detectedPatterns.add("gitops");
            analysis.detectedPatterns.add("declarative-deployment");
        }
        
        if (technologies.contains("Apache Kafka") || technologies.contains("Kinesis")) {
            analysis.detectedPatterns.add("event-driven");
            analysis.detectedPatterns.add("stream-processing");
        }
        
        if (technologies.contains("Prometheus") || technologies.contains("CloudWatch")) {
            analysis.detectedPatterns.add("observability");
            analysis.detectedPatterns.add("metrics-driven");
        }
        
        // Detect deployment assumptions
        Set<String> allAssumptions = new HashSet<>();
        for (String tech : technologies) {
            TechnologyNode node = DEPENDENCY_GRAPH.get(tech);
            if (node != null) {
                allAssumptions.addAll(node.deploymentAssumptions);
            }
        }
        analysis.deploymentAssumptions = allAssumptions;
        
        // Detect infrastructure assumptions
        Set<String> allInfraAssumptions = new HashSet<>();
        for (String tech : technologies) {
            TechnologyNode node = DEPENDENCY_GRAPH.get(tech);
            if (node != null) {
                allInfraAssumptions.addAll(node.infrastructureAssumptions);
            }
        }
        analysis.infrastructureAssumptions = allInfraAssumptions;
        
        log.info("[dependency-graph] Architecture intent - Patterns: {}, Deployment: {}, Infrastructure: {}", 
                analysis.detectedPatterns.size(), analysis.deploymentAssumptions.size(), 
                analysis.infrastructureAssumptions.size());
        
        return analysis;
    }

    private List<String> findMissingHardDependencies(Map<String, TechnologyNode> currentGraph) {
        List<String> missing = new ArrayList<>();
        Set<String> availableTechnologies = currentGraph.keySet();
        
        for (Map.Entry<String, TechnologyNode> entry : currentGraph.entrySet()) {
            String tech = entry.getKey();
            TechnologyNode node = entry.getValue();
            
            for (String dependency : node.hardDependencies) {
                if (!availableTechnologies.contains(dependency)) {
                    missing.add(tech + " requires " + dependency);
                }
            }
        }
        
        return missing;
    }

    private List<String> findOrphanTechnologies(Map<String, TechnologyNode> currentGraph) {
        List<String> orphans = new ArrayList<>();
        
        for (Map.Entry<String, TechnologyNode> entry : currentGraph.entrySet()) {
            String tech = entry.getKey();
            TechnologyNode node = entry.getValue();
            
            // Check if this technology has hard dependencies that are missing
            boolean hasUnmetDependencies = false;
            for (String dependency : node.hardDependencies) {
                if (!currentGraph.containsKey(dependency)) {
                    hasUnmetDependencies = true;
                    break;
                }
            }
            
            if (hasUnmetDependencies) {
                orphans.add(tech);
            }
        }
        
        return orphans;
    }

    private List<String> findIncompatibleCombinations(Map<String, TechnologyNode> currentGraph) {
        List<String> incompatible = new ArrayList<>();
        List<String> technologies = new ArrayList<>(currentGraph.keySet());
        
        for (int i = 0; i < technologies.size(); i++) {
            for (int j = i + 1; j < technologies.size(); j++) {
                String tech1 = technologies.get(i);
                String tech2 = technologies.get(j);
                
                if (isIncompatible(tech1, tech2)) {
                    incompatible.add(tech1 + " ↔ " + tech2);
                }
            }
        }
        
        return incompatible;
    }

    private boolean isIncompatible(String tech1, String tech2) {
        TechnologyNode node1 = DEPENDENCY_GRAPH.get(tech1);
        TechnologyNode node2 = DEPENDENCY_GRAPH.get(tech2);
        
        if (node1 == null || node2 == null) return false;
        
        // Check orchestration platform compatibility
        if (!node1.compatibleOrchestrationPlatforms.isEmpty() && 
            !node2.compatibleOrchestrationPlatforms.isEmpty()) {
            
            Set<String> intersection = new HashSet<>(node1.compatibleOrchestrationPlatforms);
            intersection.retainAll(node2.compatibleOrchestrationPlatforms);
            
            if (intersection.isEmpty()) {
                return true; // No common orchestration platforms
            }
        }
        
        // Check deployment assumption conflicts
        Set<String> conflictingAssumptions = Set.of(
            "kubernetes-native", "ecs-native",
            "helm-charts", "task-definition",
            "operator-pattern", "serverless"
        );
        
        for (String assumption1 : node1.deploymentAssumptions) {
            for (String assumption2 : node2.deploymentAssumptions) {
                if (conflictingAssumptions.contains(assumption1) && 
                    conflictingAssumptions.contains(assumption2) && 
                    !assumption1.equals(assumption2)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    private Map<String, List<String>> identifyEcosystemClusters(Map<String, TechnologyNode> currentGraph) {
        Map<String, List<String>> clusters = new HashMap<>();
        
        // Group by orchestration platform
        Map<String, List<String>> platformGroups = new HashMap<>();
        for (Map.Entry<String, TechnologyNode> entry : currentGraph.entrySet()) {
            String tech = entry.getKey();
            TechnologyNode node = entry.getValue();
            
            for (String platform : node.compatibleOrchestrationPlatforms) {
                platformGroups.computeIfAbsent(platform, k -> new ArrayList<>()).add(tech);
            }
        }
        
        // Only include clusters with multiple technologies
        for (Map.Entry<String, List<String>> entry : platformGroups.entrySet()) {
            if (entry.getValue().size() > 1) {
                clusters.put(entry.getKey() + "_ecosystem", entry.getValue());
            }
        }
        
        return clusters;
    }

    private double calculateArchitectureIntegrityScore(DependencyGraphAnalysis analysis) {
        double score = 1.0; // Start with perfect score
        
        // Penalize missing hard dependencies heavily
        score -= analysis.missingHardDependencies.size() * 0.3;
        
        // Penalize orphan technologies
        score -= analysis.orphanTechnologies.size() * 0.2;
        
        // Penalize incompatible combinations
        score -= analysis.incompatibleCombinations.size() * 0.25;
        
        // Reward ecosystem clusters
        score += Math.min(0.3, analysis.ecosystemClusters.size() * 0.1);
        
        return Math.max(0.0, Math.min(1.0, score));
    }

    // Data classes
    public static class TechnologyNode {
        public final String name;
        public final String category;
        public final String role;
        public final Set<String> hardDependencies;
        public final Set<String> optionalDependencies;
        public final Set<String> ecosystemAttachments;
        public final Set<String> compatibleOrchestrationPlatforms;
        public final Set<String> deploymentAssumptions;
        public final Set<String> infrastructureAssumptions;

        public TechnologyNode(String name, String category, String role,
                            Set<String> hardDependencies, Set<String> optionalDependencies,
                            Set<String> ecosystemAttachments, Set<String> compatibleOrchestrationPlatforms,
                            Set<String> deploymentAssumptions, Set<String> infrastructureAssumptions) {
            this.name = name;
            this.category = category;
            this.role = role;
            this.hardDependencies = hardDependencies;
            this.optionalDependencies = optionalDependencies;
            this.ecosystemAttachments = ecosystemAttachments;
            this.compatibleOrchestrationPlatforms = compatibleOrchestrationPlatforms;
            this.deploymentAssumptions = deploymentAssumptions;
            this.infrastructureAssumptions = infrastructureAssumptions;
        }
    }

    public static class EcosystemMigration {
        public final String fromTechnology;
        public final String toTechnology;
        public final Map<String, String> technologyReplacements;
        public final Set<String> removedAssumptions;
        public final Set<String> addedAssumptions;

        public EcosystemMigration(String fromTechnology, String toTechnology,
                                Map<String, String> technologyReplacements,
                                Set<String> removedAssumptions, Set<String> addedAssumptions) {
            this.fromTechnology = fromTechnology;
            this.toTechnology = toTechnology;
            this.technologyReplacements = technologyReplacements;
            this.removedAssumptions = removedAssumptions;
            this.addedAssumptions = addedAssumptions;
        }
    }

    public static class DependencyGraphAnalysis {
        public Set<String> recognizedTechnologies = new HashSet<>();
        public Set<String> unrecognizedTechnologies = new HashSet<>();
        public List<String> missingHardDependencies = new ArrayList<>();
        public List<String> orphanTechnologies = new ArrayList<>();
        public List<String> incompatibleCombinations = new ArrayList<>();
        public Map<String, List<String>> ecosystemClusters = new HashMap<>();
        public double architectureIntegrityScore = 0.0;
    }

    public static class ReplacementSimulation {
        public String originalTechnology;
        public String replacementTechnology;
        public EcosystemMigration ecosystemMigration;
        public Map<String, String> cascadingReplacements = new HashMap<>();
        public List<String> removedIncompatibleTechnologies = new ArrayList<>();
        public List<String> newOrphanTechnologies = new ArrayList<>();
        public List<String> newIncompatibleCombinations = new ArrayList<>();
        public double beforeIntegrityScore = 0.0;
        public double afterIntegrityScore = 0.0;
        public double integrityDelta = 0.0;
        public boolean isValid = false;
    }

    public static class ArchitectureIntentAnalysis {
        public Set<String> detectedPatterns = new HashSet<>();
        public Set<String> deploymentAssumptions = new HashSet<>();
        public Set<String> infrastructureAssumptions = new HashSet<>();
    }
}