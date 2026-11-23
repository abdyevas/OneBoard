package com.oneboard.adaptation;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Dictionary-based skill extraction. Deliberately simple and deterministic: it is the baseline that works
 * without any API key, and also feeds hints to the LLM adapter.
 */
@Component
public class SkillExtractor {

    /** canonical name -> aliases (lowercase). The canonical name itself is always an alias too. */
    private static final Map<String, List<String>> DICTIONARY = new LinkedHashMap<>();

    static {
        // languages
        skill("Java"); skill("Kotlin"); skill("Scala"); skill("Groovy");
        skill("Python"); skill("Golang", "go developer", "go engineer", "go (golang)"); skill("Rust"); skill("C++", "cpp");
        skill("C#", "csharp"); skill(".NET", "dotnet", "asp.net"); skill("PHP"); skill("Ruby"); skill("Ruby on Rails", "rails");
        skill("JavaScript", "js", "ecmascript"); skill("TypeScript"); skill("Swift"); skill("Objective-C");
        skill("Dart"); skill("Elixir"); skill("Haskell"); skill("Clojure"); skill("Bash", "shell scripting");
        skill("SQL"); skill("PL/SQL", "plsql");
        // JVM / backend
        skill("Spring", "spring framework"); skill("Spring Boot", "springboot"); skill("Spring Cloud");
        skill("Spring Security"); skill("Hibernate"); skill("JPA"); skill("Quarkus"); skill("Micronaut");
        skill("Maven"); skill("Gradle"); skill("JUnit"); skill("Mockito"); skill("Testcontainers");
        skill("Django"); skill("Flask"); skill("FastAPI"); skill("Laravel"); skill("Symfony");
        skill("Node.js", "nodejs"); skill("Express.js", "expressjs"); skill("NestJS", "nest.js");
        skill("GraphQL"); skill("REST API", "restful", "rest apis", "rest-api"); skill("gRPC");
        skill("Microservices", "microservice", "micro-services"); skill("WebSockets", "websocket");
        // frontend
        skill("React", "react.js", "reactjs"); skill("Angular"); skill("Vue", "vue.js", "vuejs"); skill("Svelte");
        skill("Next.js", "nextjs"); skill("Redux"); skill("HTML", "html5"); skill("CSS", "css3");
        skill("Tailwind", "tailwindcss"); skill("Webpack"); skill("Vite");
        // mobile
        skill("Android"); skill("iOS"); skill("Flutter"); skill("React Native");
        // data
        skill("PostgreSQL", "postgres"); skill("MySQL"); skill("MariaDB"); skill("Oracle"); skill("MS SQL", "sql server", "mssql");
        skill("MongoDB", "mongo"); skill("Redis"); skill("Cassandra"); skill("Elasticsearch", "elastic search", "opensearch");
        skill("ClickHouse"); skill("DynamoDB"); skill("Snowflake"); skill("BigQuery");
        skill("Kafka", "apache kafka"); skill("RabbitMQ"); skill("ActiveMQ"); skill("NATS");
        skill("Spark", "apache spark", "pyspark"); skill("Hadoop"); skill("Airflow"); skill("dbt");
        skill("Pandas"); skill("NumPy"); skill("ETL");
        // ML
        skill("Machine Learning", "ml"); skill("Deep Learning"); skill("PyTorch"); skill("TensorFlow");
        skill("scikit-learn", "sklearn"); skill("LLM", "llms", "large language models"); skill("NLP");
        // cloud / devops
        skill("AWS", "amazon web services"); skill("GCP", "google cloud"); skill("Azure");
        skill("Docker"); skill("Kubernetes", "k8s"); skill("Helm"); skill("Terraform"); skill("Ansible");
        skill("CI/CD", "ci / cd", "continuous integration"); skill("Jenkins"); skill("GitLab CI", "gitlab-ci");
        skill("GitHub Actions"); skill("Git"); skill("Linux"); skill("Nginx");
        skill("Prometheus"); skill("Grafana"); skill("OpenTelemetry"); skill("ELK");
        // practices
        skill("TDD", "test-driven development"); skill("DDD", "domain-driven design"); skill("Event Sourcing");
        skill("CQRS"); skill("OAuth", "oauth2", "oauth 2.0"); skill("Agile"); skill("Scrum"); skill("Kanban");
        skill("System Design"); skill("Distributed Systems");
    }

    private static void skill(String canonical, String... aliases) {
        List<String> all = new ArrayList<>();
        all.add(canonical.toLowerCase(Locale.ROOT));
        for (String a : aliases) all.add(a.toLowerCase(Locale.ROOT));
        DICTIONARY.put(canonical, all);
    }

    private final Map<String, Pattern> patterns = new LinkedHashMap<>();

    public SkillExtractor() {
        DICTIONARY.forEach((canonical, aliases) -> {
            String alt = String.join("|", aliases.stream().map(Pattern::quote).toList());
            // not glued to letters/digits/+/# on either side, so "java" doesn't match "javascript"
            // and "c++" doesn't match inside other tokens; a trailing "." (end of sentence) is fine.
            patterns.put(canonical, Pattern.compile("(?<![\\p{L}\\p{N}+#])(?:" + alt + ")(?![\\p{L}\\p{N}+#]|\\.[\\p{L}\\p{N}])",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
        });
    }

    /** Skills found in the text, ordered by first mention. */
    public List<String> extract(String text) {
        if (text == null || text.isBlank()) return List.of();
        record Hit(String skill, int pos) {}
        List<Hit> hits = new ArrayList<>();
        patterns.forEach((skill, p) -> {
            Matcher m = p.matcher(text);
            if (m.find()) hits.add(new Hit(skill, m.start()));
        });
        hits.sort(Comparator.comparingInt(Hit::pos));
        return hits.stream().map(Hit::skill).toList();
    }

    public record SkillMatch(List<String> vacancySkills, List<String> matched, List<String> missing,
                             List<String> extraCvSkills, int score) {}

    /**
     * Compares the skills of a CV with those of a vacancy. Score = share of vacancy skills present in the CV,
     * with skills from the vacancy's tags / title counted twice (they are usually the "must haves").
     */
    public SkillMatch match(String cvText, String vacancyText, Collection<String> vacancyTags, String vacancyTitle) {
        Set<String> cvSkills = new LinkedHashSet<>(extract(cvText));
        String priorityText = String.join(" \n ", vacancyTags == null ? List.of() : vacancyTags) + " \n " + Objects.toString(vacancyTitle, "");
        Set<String> priority = new LinkedHashSet<>(extract(priorityText));

        LinkedHashSet<String> vacancySkills = new LinkedHashSet<>(priority);
        vacancySkills.addAll(extract(vacancyText));

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        int total = 0;
        int got = 0;
        for (String s : vacancySkills) {
            int weight = priority.contains(s) ? 2 : 1;
            total += weight;
            if (cvSkills.contains(s)) {
                matched.add(s);
                got += weight;
            } else {
                missing.add(s);
            }
        }
        List<String> extra = cvSkills.stream().filter(s -> !vacancySkills.contains(s)).toList();
        int score = total == 0 ? 0 : Math.round(100f * got / total);
        return new SkillMatch(List.copyOf(vacancySkills), matched, missing, extra, score);
    }
}
