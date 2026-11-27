package com.oneboard.vacancy.source;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.oneboard.vacancy.Vacancy;
import com.oneboard.vacancy.VacancyRepository;

/** Parsing of the Azerbaijani boards, against canned responses shaped like the real ones (Nov 2026). */
class AzSourcesTest {

    /** No waiting in tests; delays themselves are covered by PoliteHttpTest. */
    static class NoWait extends PoliteHttp {
        @Override public void await(String host, Duration delay) {}
    }

    // ---------------- jobsearch.az ----------------

    private static final String JS_LIST = """
            {"items":[{"id":156159,"title":"Backend (Java) proqramlaşdırma üzrə Aparıcı Mütəxəssis","is_new":false,
              "created_at":"2026-11-16T00:00:00+04:00","slug":"golden-pay-backend-java-156159",
              "company":{"title":"Golden Pay","id":4286},"salary":"","hide_company":false},
             {"id":157000,"title":"Mühasib","created_at":"2026-11-27T00:00:00+04:00","slug":"gizli-muhasib-157000",
              "company":{"title":"Secret LLC"},"salary":"1500","hide_company":true}],
             "next":"https://unsu.jobsearch.az/api-az/vacancies-az?page=2"}
            """;

    private static final String JS_DETAIL = """
            {"id":156159,"title":"Backend (Java) proqramlaşdırma üzrə Aparıcı Mütəxəssis",
             "created_at":"2026-11-16T00:00:00+04:00","deadline_at":"2026-11-30T00:00:00+04:00",
             "company":{"title":"Golden Pay"},"category":{"title":"İT, Proqram Təminatı"},
             "salary":0,"hide_company":false,
             "text":"<p><strong>Tələblər:</strong></p><ul><li>Java 17+</li><li>Spring Boot</li></ul>"}
            """;

    @Test
    void jobsearchListGivesSummaries() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("https://unsu.jobsearch.az/api-az/vacancies-az?hl=az&q=java"))
                .andRespond(withSuccess(JS_LIST, MediaType.APPLICATION_JSON));

        var jobs = new JobsearchAzSource(b.build(), new NoWait()).fetch("java");

        server.verify();
        assertEquals(2, jobs.size());
        var first = jobs.get(0);
        assertEquals("156159", first.externalId());
        assertEquals("Golden Pay", first.company());
        assertEquals("https://jobsearch.az/vacancies/golden-pay-backend-java-156159", first.url());
        assertNull(first.description(), "description is loaded later from the details request");
        assertNull(first.salary(), "empty salary means not specified");
        assertEquals(Instant.parse("2026-11-15T20:00:00Z"), first.publishedAt());

        var hidden = jobs.get(1);
        assertNull(hidden.company(), "company hidden by the employer must stay hidden");
        assertEquals("1500 AZN", hidden.salary());
    }

    @Test
    void jobsearchDetailsGiveTextCategoryAndDeadline() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("https://unsu.jobsearch.az/api-az/vacancies-az/golden-pay-backend-java-156159?hl=az"))
                .andRespond(withSuccess(JS_DETAIL, MediaType.APPLICATION_JSON));
        Vacancy summary = new Vacancy("jobsearch", "156159");
        summary.setUrl("https://jobsearch.az/vacancies/golden-pay-backend-java-156159");

        var d = new JobsearchAzSource(b.build(), new NoWait()).fetchDetails(summary).orElseThrow();

        assertEquals("Tələblər:\n\n- Java 17+\n- Spring Boot", d.description());
        assertEquals(List.of("İT, Proqram Təminatı"), d.tags());
        assertEquals(Instant.parse("2026-11-29T20:00:00Z"), d.deadline());
        assertNull(d.salary());
    }

    // ---------------- smartjob.az ----------------

    private static final String SJ_SITEMAP = """
            <?xml version="1.0" encoding="UTF-8"?><urlset>
            <url><loc>https://smartjob.az/vakansiyalar/old-job-1</loc><lastmod>2026-11-01T10:00:00.000Z</lastmod></url>
            <url><loc>https://smartjob.az/vakansiyalar/satici-kassir-36612</loc><lastmod>2026-11-27T05:30:00.000Z</lastmod></url>
            </urlset>
            """;

    private static String page(String jsonLd) {
        return "<html><head><script type=\"application/ld+json\">{\"@context\":\"https://schema.org\",\"@graph\":[{\"@type\":\"Organization\",\"name\":\"SmartJob.az\"}]}</script>"
                + "<script type=\"application/ld+json\">" + jsonLd + "</script></head><body>...</body></html>";
    }

    private static final String SJ_POSTING = """
            [{"@context":"https://schema.org","@type":"JobPosting","title":"Satıcı - Kassir ( Elmlər, Nərimanov )",
              "description":"<p>Vəzifə öhdəlikləri</p><ul><li>Kassa əməliyyatları</li></ul>",
              "datePosted":"2023-08-19T15:57:58.000Z","employmentType":"FULL_TIME","industry":"Digər",
              "occupationalCategory":"Satıcı","hiringOrganization":{"@type":"Organization","name":"Balli Bisi"},
              "jobLocation":{"@type":"Place","address":{"@type":"PostalAddress","addressLocality":"Bakı","addressCountry":"AZ"}},
              "baseSalary":{"@type":"MonetaryAmount","currency":"AZN","value":{"@type":"QuantitativeValue","minValue":600,"maxValue":700}},
              "url":"https://smartjob.az/vakansiyalar/satici-kassir-36612"},
             {"@type":"BreadcrumbList","itemListElement":[]}]
            """;

    private static final String SJ_EXPIRED = """
            {"@type":"JobPosting","title":"Old","description":"x","validThrough":"2026-11-02T00:00:00Z"}
            """;

    @Test
    void smartjobReadsNewestJobPostingsFromSitemap() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("https://smartjob.az/sitemaps/jobs/1"))
                .andRespond(withSuccess(SJ_SITEMAP, MediaType.APPLICATION_XML));
        // newest first
        server.expect(requestTo("https://smartjob.az/vakansiyalar/satici-kassir-36612"))
                .andRespond(withSuccess(page(SJ_POSTING), MediaType.TEXT_HTML));
        server.expect(requestTo("https://smartjob.az/vakansiyalar/old-job-1"))
                .andRespond(withSuccess(page(SJ_EXPIRED), MediaType.TEXT_HTML));
        VacancyRepository repo = mock(VacancyRepository.class);
        when(repo.findBySourceAndExternalId(any(), any())).thenReturn(Optional.empty());

        var jobs = new SmartjobAzSource(b.build(), new NoWait(), repo).fetch(null);

        server.verify();
        assertEquals(1, jobs.size(), "expired posting is skipped");
        var v = jobs.getFirst();
        assertEquals("satici-kassir-36612", v.externalId());
        assertEquals("Satıcı - Kassir ( Elmlər, Nərimanov )", v.title());
        assertEquals("Balli Bisi", v.company());
        assertEquals("Bakı", v.location());
        assertEquals("Tam ştat", v.employmentType());
        assertEquals("600–700 AZN", v.salary());
        assertEquals(List.of("Satıcı"), v.tags(), "generic 'Digər' category is dropped");
        assertEquals("Vəzifə öhdəlikləri\n\n- Kassa əməliyyatları", v.description());
        assertEquals(Instant.parse("2026-11-27T05:30:00Z"), v.publishedAt(), "renewal date from sitemap");
    }

    @Test
    void smartjobSkipsVacanciesUnchangedSinceLastFetch() {
        RestClient.Builder b = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(b).build();
        server.expect(requestTo("https://smartjob.az/sitemaps/jobs/1"))
                .andRespond(withSuccess(SJ_SITEMAP, MediaType.APPLICATION_XML));
        // no page requests expected
        Vacancy known = new Vacancy("smartjob", "x");
        known.setFetchedAt(Instant.parse("2026-11-28T00:00:00Z"));
        VacancyRepository repo = mock(VacancyRepository.class);
        when(repo.findBySourceAndExternalId(any(), any())).thenReturn(Optional.of(known));

        var jobs = new SmartjobAzSource(b.build(), new NoWait(), repo).fetch(null);

        server.verify();
        assertTrue(jobs.isEmpty());
    }
}
