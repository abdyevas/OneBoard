package com.oneboard;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.oneboard.adaptation.AdaptationService;
import com.oneboard.aggregation.AggregationService;
import com.oneboard.application.ApplicationStatus;
import com.oneboard.application.JobApplicationService;
import com.oneboard.cv.CvService;
import com.oneboard.vacancy.VacancyDtos.CreateVacancyRequest;
import com.oneboard.vacancy.VacancyService;
import com.oneboard.vacancy.source.FetchedVacancy;
import com.oneboard.vacancy.source.VacancySource;

@SpringBootTest
class OneboardApplicationTests {

    @TestConfiguration
    static class FakeSourceConfig {
        @Bean
        VacancySource fakeSource() {
            return new VacancySource() {
                @Override public String id() { return "fake"; }
                @Override public String displayName() { return "Fake"; }
                @Override public List<FetchedVacancy> fetch(String query) {
                    return List.of(new FetchedVacancy("1", "Senior Kotlin Engineer", "Acme", "Berlin", true,
                            "full_time", null, "Kotlin, Spring Boot, Kafka", "https://example.com/1",
                            List.of("kotlin"), Instant.now()));
                }
            };
        }
    }

    @Autowired CvService cvs;
    @Autowired VacancyService vacancies;
    @Autowired AdaptationService adaptations;
    @Autowired AggregationService aggregation;
    @Autowired JobApplicationService applications;

    @Test
    void aggregationUpsertsWithoutDuplicates() {
        var first = aggregation.refresh("kotlin", Set.of("fake"));
        var second = aggregation.refresh("kotlin", Set.of("fake"));

        assertEquals(1, first.results().getFirst().created());
        assertEquals(0, second.results().getFirst().created());
        assertEquals(1, second.results().getFirst().updated());
        assertEquals(1L, vacancies.stats().get("fake"));
    }

    @Test
    void adaptsCvWithKeywordMethodWhenClaudeIsNotConfigured() {
        var cv = cvs.create("Backend", "Jane Doe\nJava developer. Spring Boot, PostgreSQL, Docker.");
        var vacancy = vacancies.createManual(new CreateVacancyRequest("Java Developer", "Acme", "Remote", true,
                null, "We need Java, Spring Boot, Kafka.", null, List.of("Java")));

        var result = adaptations.adapt(cv.getId(), vacancy.getId(), "auto");

        assertEquals("keyword", result.getMethod());
        assertTrue(result.getMatchedSkills().containsAll(List.of("Java", "Spring Boot")));
        assertEquals(List.of("Kafka"), result.getMissingSkills());
        assertTrue(result.getContent().contains("Jane Doe"), "original CV must be preserved");
        assertFalse(result.getContent().contains("Kafka"), "must not invent skills");

        var ranked = adaptations.rankVacancies(cv.getId(), null, 10);
        assertEquals(vacancy.getId(), ranked.getFirst().vacancyId());
    }

    @Test
    void tracksApplicationStatusPerVacancy() {
        var vacancy = vacancies.createManual(new CreateVacancyRequest("QA Engineer", "Acme", null, false, null,
                "Testing", "https://example.com/apply", null));

        var applied = applications.set(vacancy.getId(), ApplicationStatus.APPLIED, null);
        var moved = applications.set(vacancy.getId(), ApplicationStatus.INTERVIEW, "call on Monday");

        assertEquals(applied.getId(), moved.getId(), "one record per vacancy");
        assertEquals(ApplicationStatus.INTERVIEW, applications.forVacancy(vacancy.getId()).orElseThrow().getStatus());
        assertEquals(applied.getAppliedAt(), moved.getAppliedAt(), "applied date is kept on status change");

        applications.remove(vacancy.getId());
        assertTrue(applications.forVacancy(vacancy.getId()).isEmpty());
        assertThrows(com.oneboard.common.NotFoundException.class,
                () -> applications.set(999_999L, ApplicationStatus.APPLIED, null));
    }

    @Test
    void claudeMethodFailsClearlyWithoutKey() {
        var cv = cvs.create("x", "Java");
        var vacancy = vacancies.createManual(new CreateVacancyRequest("Java", null, null, false, null, "Java", null, null));
        var ex = assertThrows(IllegalStateException.class, () -> adaptations.adapt(cv.getId(), vacancy.getId(), "claude"));
        assertTrue(ex.getMessage().contains("not configured"));
    }
}
