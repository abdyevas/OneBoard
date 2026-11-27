package com.oneboard.vacancy.source;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.oneboard.common.Html;
import com.oneboard.vacancy.Vacancy;

/**
 * jobsearch.az — the vacancy feed its own website loads (unsu.jobsearch.az/api-az).
 * <p>
 * Rules we follow (checked Nov 2026): jobsearch.az publishes no terms of use; its robots.txt allows
 * everything except {@code /unsu}, and the API host's robots.txt sets <b>Crawl-delay: 30</b>.
 * So we send at most one request per 30 seconds to that host: the list (30 vacancies) on refresh,
 * then one vacancy's details at a time in the background. Every vacancy links back to jobsearch.az.
 */
@Component
public class JobsearchAzSource implements VacancySource {

    static final String API_HOST = "unsu.jobsearch.az";
    private static final String API = "https://" + API_HOST + "/api-az";
    private static final String SITE = "https://jobsearch.az/vacancies/";
    /** From https://unsu.jobsearch.az/robots.txt */
    static final Duration CRAWL_DELAY = Duration.ofSeconds(30);

    private final RestClient http;
    private final PoliteHttp polite;

    public JobsearchAzSource(@Qualifier("sourceRestClient") RestClient http, PoliteHttp polite) {
        this.http = http;
        this.polite = polite;
    }

    @Override public String id() { return "jobsearch"; }
    @Override public String displayName() { return "jobsearch.az"; }
    @Override public String detailsHost() { return API_HOST; }
    @Override public Duration detailsDelay() { return CRAWL_DELAY; }

    @Override
    public List<FetchedVacancy> fetch(String query) {
        polite.await(API_HOST, CRAWL_DELAY);
        ListResponse resp = http.get()
                .uri(API + "/vacancies-az?hl=az&q={q}", query == null ? "" : query.trim())
                .header("X-Requested-With", "XMLHttpRequest")
                .retrieve()
                .body(ListResponse.class);
        if (resp == null || resp.items() == null) return List.of();
        List<FetchedVacancy> result = new ArrayList<>();
        for (Item i : resp.items()) {
            if (i.slug() == null || i.title() == null) continue;
            result.add(new FetchedVacancy(
                    String.valueOf(i.id()),
                    i.title().strip(),
                    companyName(i.company(), i.hideCompany()),
                    null, false, null,
                    salary(i.salary()),
                    null, // description comes from the details request
                    SITE + i.slug(),
                    List.of(),
                    parseDate(i.createdAt()),
                    null));
        }
        return result;
    }

    @Override
    public Optional<FetchedVacancy> fetchDetails(Vacancy summary) {
        String slug = slugOf(summary.getUrl());
        if (slug == null) return Optional.empty();
        Detail d = http.get()
                .uri(API + "/vacancies-az/{slug}?hl=az", slug)
                .header("X-Requested-With", "XMLHttpRequest")
                .retrieve()
                .body(Detail.class);
        if (d == null) return Optional.empty();
        List<String> tags = new ArrayList<>();
        if (d.category() != null && d.category().title() != null) tags.add(d.category().title());
        return Optional.of(new FetchedVacancy(
                summary.getExternalId(),
                d.title() == null ? null : d.title().strip(),
                d.company() == null ? null : companyName(d.company(), d.hideCompany()),
                null, false, null,
                salary(d.salary()),
                Html.toText(d.text()),
                null,
                tags,
                parseDate(d.createdAt()),
                parseDate(d.deadlineAt())));
    }

    static String slugOf(String url) {
        if (url == null || !url.startsWith(SITE)) return null;
        String s = url.substring(SITE.length());
        int q = s.indexOf('?');
        return q >= 0 ? s.substring(0, q) : s;
    }

    private static String companyName(Company c, Boolean hidden) {
        if (Boolean.TRUE.equals(hidden) || c == null || c.title() == null || c.title().isBlank()) return null;
        return c.title().strip();
    }

    /** The API returns salary as "" / 0 when not specified, otherwise a number or text. */
    private static String salary(Object raw) {
        if (raw == null) return null;
        String s = raw.toString().strip();
        if (s.isEmpty() || s.equals("0")) return null;
        return s.matches("\\d+(\\.\\d+)?") ? s.replaceFirst("\\.0+$", "") + " AZN" : s;
    }

    private static Instant parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ListResponse(List<Item> items, String next) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(long id, String title, String slug,
                @JsonProperty("created_at") String createdAt,
                Company company, Object salary,
                @JsonProperty("hide_company") Boolean hideCompany) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Company(String title) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Category(String title) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Detail(String title, String text,
                  @JsonProperty("created_at") String createdAt,
                  @JsonProperty("deadline_at") String deadlineAt,
                  Company company, Category category, Object salary,
                  @JsonProperty("hide_company") Boolean hideCompany) {}
}
