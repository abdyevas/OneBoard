package com.oneboard.vacancy.source;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class JoobleSourceTest {

    private static final String RESPONSE = """
            {"totalCount": 1, "jobs": [{
              "id": 1234567890,
              "title": "<b>Java</b> Developer",
              "location": "Bakı",
              "snippet": "We need&nbsp;<b>Java</b> and Spring Boot...",
              "salary": "2500 AZN",
              "source": "jobsearch.az",
              "type": "Full-time",
              "link": "https://az.jooble.org/jdp/123",
              "company": "ABC MMC",
              "updated": "2026-11-20T09:15:00.0000000"
            }]}
            """;

    private static JoobleProperties props(String key) {
        return new JoobleProperties(key, "https://az.jooble.org", "Azərbaycan", 50, 2);
    }

    @Test
    void mapsJobsFromApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://az.jooble.org/api/test-key"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.keywords").value("java"))
                .andExpect(jsonPath("$.location").value("Azərbaycan"))
                .andExpect(jsonPath("$.page").value("1"))
                .andRespond(withSuccess(RESPONSE, MediaType.APPLICATION_JSON));

        var source = new JoobleSource(builder.build(), props("test-key"));
        var jobs = source.fetch("java");

        server.verify();
        assertEquals(1, jobs.size());
        var v = jobs.getFirst();
        assertEquals("1234567890", v.externalId());
        assertEquals("Java Developer", v.title());
        assertEquals("ABC MMC", v.company());
        assertEquals("Bakı", v.location());
        assertEquals("2500 AZN", v.salary());
        assertEquals("We need Java and Spring Boot...", v.description());
        assertEquals("https://az.jooble.org/jdp/123", v.url());
        assertEquals(Instant.parse("2026-11-20T09:15:00Z"), v.publishedAt());
        assertFalse(v.remote());
    }

    @Test
    void reportsInvalidKeyClearly() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://az.jooble.org/api/bad")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        var source = new JoobleSource(builder.build(), props("bad"));
        var ex = assertThrows(IllegalStateException.class, () -> source.fetch("java"));
        assertTrue(ex.getMessage().contains("403"));
    }

    @Test
    void disabledWithoutKey() {
        assertFalse(new JoobleSource(RestClient.create(), props("")).enabled());
        assertTrue(new JoobleSource(RestClient.create(), props("k")).enabled());
    }
}
