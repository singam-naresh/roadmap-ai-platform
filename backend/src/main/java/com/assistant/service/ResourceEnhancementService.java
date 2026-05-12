package com.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * PHASE 7.3 — Domain-Aware Resource Enhancement Service
 *
 * Replaces generic placeholder resources (GitHub, StackOverflow, LeetCode)
 * with curated, domain-specific, production-grade resources.
 *
 * Each domain has categorised resources:
 *   - Official Docs
 *   - Production Tools
 *   - Community
 *   - Hands-on Labs
 *   - Open Source Reference
 */
@Service
public class ResourceEnhancementService {

    private static final Logger log = LoggerFactory.getLogger(ResourceEnhancementService.class);

    // ─── Domain-specific curated resources ───────────────────────────────────

    private static final Map<String, List<String>> DOMAIN_RESOURCES;
    static {
        DOMAIN_RESOURCES = new HashMap<>();

        DOMAIN_RESOURCES.put("AI_ENGINEERING", List.of(
            // Official Docs
            "PyTorch Documentation — https://pytorch.org/docs/stable/index.html",
            "Hugging Face Transformers — https://huggingface.co/docs/transformers",
            "vLLM Documentation — https://docs.vllm.ai/en/latest/",
            "DeepSpeed Documentation — https://www.deepspeed.ai/docs/",
            "TensorRT-LLM — https://nvidia.github.io/TensorRT-LLM/",
            "NVIDIA Triton Inference Server — https://docs.nvidia.com/deeplearning/triton-inference-server/",
            // Production Tools
            "Weights & Biases — https://docs.wandb.ai/",
            "MLflow Documentation — https://mlflow.org/docs/latest/index.html",
            "Ray Serve — https://docs.ray.io/en/latest/serve/index.html",
            "Kubeflow — https://www.kubeflow.org/docs/",
            "PEFT (Parameter-Efficient Fine-Tuning) — https://huggingface.co/docs/peft",
            "LangSmith — https://docs.smith.langchain.com/",
            // Hands-on Labs
            "Hugging Face Course — https://huggingface.co/learn/nlp-course",
            "Fast.ai Practical Deep Learning — https://course.fast.ai/",
            "NVIDIA Deep Learning Institute — https://www.nvidia.com/en-us/training/",
            // Open Source Reference
            "LLaMA Factory — https://github.com/hiyouga/LLaMA-Factory",
            "Axolotl Fine-tuning — https://github.com/OpenAccess-AI-Collective/axolotl"
        ));

        DOMAIN_RESOURCES.put("JAVA_BACKEND", List.of(
            // Official Docs
            "Spring Boot Reference — https://docs.spring.io/spring-boot/docs/current/reference/html/",
            "Spring Security — https://docs.spring.io/spring-security/reference/",
            "Hibernate ORM — https://hibernate.org/orm/documentation/",
            "PostgreSQL Documentation — https://www.postgresql.org/docs/",
            "Apache Kafka Documentation — https://kafka.apache.org/documentation/",
            // Production Tools
            "Baeldung Java Tutorials — https://www.baeldung.com/",
            "Testcontainers — https://testcontainers.com/guides/",
            "Micrometer Observability — https://micrometer.io/docs",
            "Docker Documentation — https://docs.docker.com/",
            "Kubernetes Documentation — https://kubernetes.io/docs/home/",
            // Community
            "Spring Community Forum — https://community.spring.io/",
            "r/java — https://www.reddit.com/r/java/",
            // Hands-on Labs
            "Spring Guides — https://spring.io/guides",
            "Java Brains YouTube — https://www.youtube.com/@JavaBrainsChannel",
            // Open Source Reference
            "Spring PetClinic — https://github.com/spring-projects/spring-petclinic",
            "Realworld Spring Boot — https://github.com/gothinkster/spring-boot-realworld-example-app"
        ));

        DOMAIN_RESOURCES.put("REACT_FRONTEND", List.of(
            // Official Docs
            "React Documentation — https://react.dev/",
            "TypeScript Handbook — https://www.typescriptlang.org/docs/handbook/",
            "Next.js Documentation — https://nextjs.org/docs",
            "Vite Documentation — https://vitejs.dev/guide/",
            "TanStack Query — https://tanstack.com/query/latest/docs/framework/react/overview",
            // Production Tools
            "Tailwind CSS — https://tailwindcss.com/docs",
            "Radix UI Primitives — https://www.radix-ui.com/primitives/docs/overview/introduction",
            "Framer Motion — https://www.framer.com/motion/",
            "Vitest — https://vitest.dev/guide/",
            "Playwright — https://playwright.dev/docs/intro",
            // Community
            "React Newsletter — https://thisweekinreact.com/",
            "r/reactjs — https://www.reddit.com/r/reactjs/",
            // Hands-on Labs
            "React Official Tutorial — https://react.dev/learn/tutorial-tic-tac-toe",
            "Josh Comeau CSS for JS — https://css-for-js.dev/",
            // Open Source Reference
            "Bulletproof React — https://github.com/alan2207/bulletproof-react",
            "shadcn/ui — https://ui.shadcn.com/docs"
        ));

        DOMAIN_RESOURCES.put("DEVOPS", List.of(
            // Official Docs
            "Kubernetes Documentation — https://kubernetes.io/docs/home/",
            "Terraform Documentation — https://developer.hashicorp.com/terraform/docs",
            "ArgoCD Documentation — https://argo-cd.readthedocs.io/en/stable/",
            "Prometheus Documentation — https://prometheus.io/docs/introduction/overview/",
            "Istio Documentation — https://istio.io/latest/docs/",
            // Production Tools
            "Helm Documentation — https://helm.sh/docs/",
            "Grafana Documentation — https://grafana.com/docs/grafana/latest/",
            "Vault by HashiCorp — https://developer.hashicorp.com/vault/docs",
            "Falco Runtime Security — https://falco.org/docs/",
            "OpenTelemetry — https://opentelemetry.io/docs/",
            // Community
            "CNCF Landscape — https://landscape.cncf.io/",
            "DevOps Subreddit — https://www.reddit.com/r/devops/",
            // Hands-on Labs
            "Kubernetes the Hard Way — https://github.com/kelseyhightower/kubernetes-the-hard-way",
            "KillerCoda Interactive Labs — https://killercoda.com/",
            // Open Source Reference
            "Awesome Kubernetes — https://github.com/ramitsurana/awesome-kubernetes",
            "Production Kubernetes — https://github.com/FairwindsOps/goldilocks"
        ));

        DOMAIN_RESOURCES.put("DATA_ENGINEERING", List.of(
            // Official Docs
            "Apache Spark Documentation — https://spark.apache.org/docs/latest/",
            "Apache Kafka Documentation — https://kafka.apache.org/documentation/",
            "Apache Airflow Documentation — https://airflow.apache.org/docs/",
            "dbt Documentation — https://docs.getdbt.com/",
            "Delta Lake Documentation — https://docs.delta.io/latest/index.html",
            // Production Tools
            "Snowflake Documentation — https://docs.snowflake.com/",
            "Databricks Documentation — https://docs.databricks.com/",
            "Great Expectations — https://docs.greatexpectations.io/",
            "Prefect Documentation — https://docs.prefect.io/",
            // Community
            "Data Engineering Weekly — https://www.dataengineeringweekly.com/",
            "r/dataengineering — https://www.reddit.com/r/dataengineering/",
            // Hands-on Labs
            "Data Engineering Zoomcamp — https://github.com/DataTalksClub/data-engineering-zoomcamp",
            "Fundamentals of Data Engineering — https://www.oreilly.com/library/view/fundamentals-of-data/9781098108298/",
            // Open Source Reference
            "Awesome Data Engineering — https://github.com/igorbarinov/awesome-data-engineering"
        ));

        DOMAIN_RESOURCES.put("SYSTEM_DESIGN", List.of(
            // Official Docs
            "AWS Architecture Center — https://aws.amazon.com/architecture/",
            "Google Cloud Architecture — https://cloud.google.com/architecture",
            "Azure Architecture Center — https://learn.microsoft.com/en-us/azure/architecture/",
            // Production Tools
            "Envoy Proxy — https://www.envoyproxy.io/docs/envoy/latest/",
            "Apache Cassandra — https://cassandra.apache.org/doc/latest/",
            "Redis Documentation — https://redis.io/docs/",
            // Community
            "High Scalability Blog — http://highscalability.com/",
            "Martin Fowler's Blog — https://martinfowler.com/",
            // Hands-on Labs
            "System Design Primer — https://github.com/donnemartin/system-design-primer",
            "ByteByteGo Newsletter — https://blog.bytebytego.com/",
            "Designing Data-Intensive Applications — https://dataintensive.net/",
            // Open Source Reference
            "Awesome Scalability — https://github.com/binhnguyennus/awesome-scalability"
        ));

        DOMAIN_RESOURCES.put("CYBERSECURITY", List.of(
            "OWASP Top 10 — https://owasp.org/www-project-top-ten/",
            "NIST Cybersecurity Framework — https://www.nist.gov/cyberframework",
            "PortSwigger Web Security Academy — https://portswigger.net/web-security",
            "TryHackMe — https://tryhackme.com/",
            "HackTheBox — https://www.hackthebox.com/",
            "CVE Database — https://cve.mitre.org/",
            "Awesome Security — https://github.com/sbilly/awesome-security"
        ));

        DOMAIN_RESOURCES.put("MOBILE_DEVELOPMENT", List.of(
            "React Native Documentation — https://reactnative.dev/docs/getting-started",
            "Flutter Documentation — https://docs.flutter.dev/",
            "Android Developer Guides — https://developer.android.com/guide",
            "Apple Developer Documentation — https://developer.apple.com/documentation/",
            "Expo Documentation — https://docs.expo.dev/",
            "Firebase Documentation — https://firebase.google.com/docs",
            "Awesome React Native — https://github.com/jondot/awesome-react-native"
        ));

        DOMAIN_RESOURCES.put("CLOUD_ENGINEERING", List.of(
            "AWS Documentation — https://docs.aws.amazon.com/",
            "Google Cloud Documentation — https://cloud.google.com/docs",
            "Azure Documentation — https://learn.microsoft.com/en-us/azure/",
            "Terraform Registry — https://registry.terraform.io/",
            "Pulumi Documentation — https://www.pulumi.com/docs/",
            "AWS Well-Architected Framework — https://aws.amazon.com/architecture/well-architected/",
            "Cloud Native Computing Foundation — https://www.cncf.io/"
        ));

        // Fallback for general/unknown domains
        DOMAIN_RESOURCES.put("GENERAL", List.of(
            "MDN Web Docs — https://developer.mozilla.org/",
            "freeCodeCamp — https://www.freecodecamp.org/",
            "The Odin Project — https://www.theodinproject.com/",
            "roadmap.sh — https://roadmap.sh/",
            "Dev.to Community — https://dev.to/",
            "Hacker News — https://news.ycombinator.com/"
        ));
    }

    // ─── Keyword → domain mapping ─────────────────────────────────────────────

    private static final Map<String, String> KEYWORD_TO_DOMAIN = new LinkedHashMap<>();
    static {
        // AI/ML keywords
        KEYWORD_TO_DOMAIN.put("pytorch", "AI_ENGINEERING");
        KEYWORD_TO_DOMAIN.put("tensorflow", "AI_ENGINEERING");
        KEYWORD_TO_DOMAIN.put("llm", "AI_ENGINEERING");
        KEYWORD_TO_DOMAIN.put("machine learning", "AI_ENGINEERING");
        KEYWORD_TO_DOMAIN.put("deep learning", "AI_ENGINEERING");
        KEYWORD_TO_DOMAIN.put("transformer", "AI_ENGINEERING");
        KEYWORD_TO_DOMAIN.put("fine-tun", "AI_ENGINEERING");
        KEYWORD_TO_DOMAIN.put("inference", "AI_ENGINEERING");
        KEYWORD_TO_DOMAIN.put("rag", "AI_ENGINEERING");
        KEYWORD_TO_DOMAIN.put("ai engineer", "AI_ENGINEERING");
        KEYWORD_TO_DOMAIN.put("nlp", "AI_ENGINEERING");
        KEYWORD_TO_DOMAIN.put("vllm", "AI_ENGINEERING");
        KEYWORD_TO_DOMAIN.put("deepspeed", "AI_ENGINEERING");
        // Java/Backend keywords
        KEYWORD_TO_DOMAIN.put("spring boot", "JAVA_BACKEND");
        KEYWORD_TO_DOMAIN.put("spring", "JAVA_BACKEND");
        KEYWORD_TO_DOMAIN.put("java backend", "JAVA_BACKEND");
        KEYWORD_TO_DOMAIN.put("hibernate", "JAVA_BACKEND");
        KEYWORD_TO_DOMAIN.put("microservice", "JAVA_BACKEND");
        KEYWORD_TO_DOMAIN.put("rest api", "JAVA_BACKEND");
        KEYWORD_TO_DOMAIN.put("java developer", "JAVA_BACKEND");
        // Frontend keywords
        KEYWORD_TO_DOMAIN.put("react", "REACT_FRONTEND");
        KEYWORD_TO_DOMAIN.put("next.js", "REACT_FRONTEND");
        KEYWORD_TO_DOMAIN.put("frontend", "REACT_FRONTEND");
        KEYWORD_TO_DOMAIN.put("typescript", "REACT_FRONTEND");
        KEYWORD_TO_DOMAIN.put("tailwind", "REACT_FRONTEND");
        KEYWORD_TO_DOMAIN.put("vite", "REACT_FRONTEND");
        // DevOps keywords
        KEYWORD_TO_DOMAIN.put("kubernetes", "DEVOPS");
        KEYWORD_TO_DOMAIN.put("terraform", "DEVOPS");
        KEYWORD_TO_DOMAIN.put("devops", "DEVOPS");
        KEYWORD_TO_DOMAIN.put("argocd", "DEVOPS");
        KEYWORD_TO_DOMAIN.put("ci/cd", "DEVOPS");
        KEYWORD_TO_DOMAIN.put("platform engineer", "DEVOPS");
        KEYWORD_TO_DOMAIN.put("sre", "DEVOPS");
        // Data Engineering keywords
        KEYWORD_TO_DOMAIN.put("data engineer", "DATA_ENGINEERING");
        KEYWORD_TO_DOMAIN.put("apache spark", "DATA_ENGINEERING");
        KEYWORD_TO_DOMAIN.put("airflow", "DATA_ENGINEERING");
        KEYWORD_TO_DOMAIN.put("dbt", "DATA_ENGINEERING");
        KEYWORD_TO_DOMAIN.put("data pipeline", "DATA_ENGINEERING");
        KEYWORD_TO_DOMAIN.put("snowflake", "DATA_ENGINEERING");
        // System Design keywords
        KEYWORD_TO_DOMAIN.put("system design", "SYSTEM_DESIGN");
        KEYWORD_TO_DOMAIN.put("distributed system", "SYSTEM_DESIGN");
        KEYWORD_TO_DOMAIN.put("scalab", "SYSTEM_DESIGN");
        KEYWORD_TO_DOMAIN.put("architecture", "SYSTEM_DESIGN");
        // Security keywords
        KEYWORD_TO_DOMAIN.put("security", "CYBERSECURITY");
        KEYWORD_TO_DOMAIN.put("cybersecurity", "CYBERSECURITY");
        KEYWORD_TO_DOMAIN.put("penetration", "CYBERSECURITY");
        // Mobile keywords
        KEYWORD_TO_DOMAIN.put("react native", "MOBILE_DEVELOPMENT");
        KEYWORD_TO_DOMAIN.put("flutter", "MOBILE_DEVELOPMENT");
        KEYWORD_TO_DOMAIN.put("mobile", "MOBILE_DEVELOPMENT");
        KEYWORD_TO_DOMAIN.put("ios", "MOBILE_DEVELOPMENT");
        KEYWORD_TO_DOMAIN.put("android", "MOBILE_DEVELOPMENT");
        // Cloud keywords
        KEYWORD_TO_DOMAIN.put("aws", "CLOUD_ENGINEERING");
        KEYWORD_TO_DOMAIN.put("azure", "CLOUD_ENGINEERING");
        KEYWORD_TO_DOMAIN.put("gcp", "CLOUD_ENGINEERING");
        KEYWORD_TO_DOMAIN.put("cloud engineer", "CLOUD_ENGINEERING");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    public List<String> enhanceResources(List<String> originalResources, String category, String userInput) {
        String detectedDomain = detectDomain(userInput, category);
        List<String> curated  = DOMAIN_RESOURCES.getOrDefault(detectedDomain, DOMAIN_RESOURCES.get("GENERAL"));

        // Start with curated domain resources (max 8 to avoid overwhelming)
        List<String> result = new ArrayList<>(curated.subList(0, Math.min(8, curated.size())));

        // Append any original resources that contain real URLs not already in the list
        if (originalResources != null) {
            for (String r : originalResources) {
                if (r != null && r.contains("http") && !isDuplicate(r, result)) {
                    result.add(r);
                }
            }
        }

        // Deduplicate by URL
        return deduplicateByUrl(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String detectDomain(String userInput, String category) {
        String lower = (userInput != null ? userInput : "").toLowerCase()
                + " " + (category != null ? category : "").toLowerCase();

        for (Map.Entry<String, String> entry : KEYWORD_TO_DOMAIN.entrySet()) {
            if (lower.contains(entry.getKey())) {
                log.debug("[resources] Detected domain: {} from keyword: {}", entry.getValue(), entry.getKey());
                return entry.getValue();
            }
        }

        // Category fallback
        if (category != null) {
            return switch (category.toLowerCase()) {
                case "coding"      -> "JAVA_BACKEND";
                case "learning"    -> "GENERAL";
                case "startup"     -> "GENERAL";
                case "productivity"-> "GENERAL";
                default            -> "GENERAL";
            };
        }

        return "GENERAL";
    }

    private boolean isDuplicate(String resource, List<String> existing) {
        String url = extractUrl(resource);
        if (url == null) return false;
        return existing.stream().anyMatch(r -> {
            String existingUrl = extractUrl(r);
            return existingUrl != null && existingUrl.equalsIgnoreCase(url);
        });
    }

    private List<String> deduplicateByUrl(List<String> resources) {
        Set<String> seenUrls = new LinkedHashSet<>();
        List<String> result  = new ArrayList<>();
        for (String r : resources) {
            String url = extractUrl(r);
            String key = url != null ? url.toLowerCase() : r.toLowerCase();
            if (seenUrls.add(key)) {
                result.add(r);
            }
        }
        return result;
    }

    private String extractUrl(String resource) {
        if (resource == null) return null;
        int idx = resource.indexOf("http");
        if (idx < 0) return null;
        String url = resource.substring(idx);
        int end = url.indexOf(' ');
        return end > 0 ? url.substring(0, end) : url;
    }
}
