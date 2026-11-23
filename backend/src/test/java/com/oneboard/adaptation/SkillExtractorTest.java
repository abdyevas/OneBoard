package com.oneboard.adaptation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class SkillExtractorTest {

    private final SkillExtractor extractor = new SkillExtractor();

    @Test
    void extractsSkillsInOrderOfMention() {
        List<String> skills = extractor.extract("We use Kotlin, Spring Boot and PostgreSQL. Experience with k8s is a plus.");
        assertEquals(List.of("Kotlin", "Spring", "Spring Boot", "PostgreSQL", "Kubernetes"), skills);
    }

    @Test
    void doesNotConfuseJavaWithJavaScript() {
        assertEquals(List.of("JavaScript"), extractor.extract("Strong JavaScript skills"));
        assertTrue(extractor.extract("Java, JavaScript").containsAll(List.of("Java", "JavaScript")));
    }

    @Test
    void handlesSymbolsAndSentenceEnd() {
        List<String> skills = extractor.extract("Languages: C++, C#. Platform: .NET and Node.js.");
        assertTrue(skills.containsAll(List.of("C++", "C#", ".NET", "Node.js")), skills.toString());
    }

    @Test
    void ignoresAmbiguousCommonWords() {
        assertTrue(extractor.extract("Let's go to the rest of the express train").isEmpty());
    }

    @Test
    void scoresMatchWithTagsWeightedHigher() {
        String cv = "Java developer with Spring Boot, Docker and PostgreSQL.";
        String vacancy = "We need Java, Spring Boot, Kafka and Docker.";
        var match = extractor.match(cv, vacancy, List.of("Java", "Kafka"), "Java Engineer");

        assertEquals(List.of("Java", "Spring", "Spring Boot", "Docker"), match.matched());
        assertEquals(List.of("Kafka"), match.missing());
        assertEquals(List.of("PostgreSQL"), match.extraCvSkills());
        // weights: Java 2 + Kafka 2 + Spring 1 + Spring Boot 1 + Docker 1 = 7, matched = 5
        assertEquals(71, match.score());
    }
}
