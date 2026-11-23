package com.oneboard.vacancy.source;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.oneboard.common.Html;

/**
 * Arbeitnow public job board API (Europe, no key required). The API has no search parameter,
 * so we page through the feed and filter by the query locally.
 */
@Component
public class ArbeitnowSource implements VacancySource {

    private static final String URL = "https://www.arbeitnow.com/api/job-board-api";
    private static final int MAX_PAGES = 3;

    private final RestClient http;

    public ArbeitnowSource(@Qualifier("sourceRestClient") RestClient http) {
        this.http = http;
    }

    @Override public String id() { return "arbeitnow"; }
    @Override public String displayName() { return "Arbeitnow"; }

    @Override
    public List<FetchedVacancy> fetch(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<FetchedVacancy> result = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            Response resp = http.get().uri(URL + "?page={p}", page).retrieve().body(Response.class);
            if (resp == null || resp.data() == null || resp.data().isEmpty()) break;
            for (Job j : resp.data()) {
                String text = Html.toText(j.description());
                if (!q.isEmpty() && !matches(q, j, text)) continue;
                result.add(new FetchedVacancy(
                        j.slug(),
                        j.title(),
                        j.companyName(),
                        j.location(),
                        j.remote(),
                        j.jobTypes() == null ? null : String.join(", ", j.jobTypes()),
                        null,
                        text,
                        j.url(),
                        j.tags(),
                        j.createdAt() > 0 ? Instant.ofEpochSecond(j.createdAt()) : null));
            }
        }
        return result;
    }

    private static boolean matches(String q, Job j, String text) {
        return contains(j.title(), q) || contains(j.companyName(), q) || contains(text, q)
                || (j.tags() != null && j.tags().stream().anyMatch(t -> contains(t, q)));
    }

    private static boolean contains(String s, String q) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(q);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(List<Job> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Job(String slug,
               @JsonProperty("company_name") String companyName,
               String title,
               String description,
               boolean remote,
               String url,
               List<String> tags,
               @JsonProperty("job_types") List<String> jobTypes,
               String location,
               @JsonProperty("created_at") long createdAt) {}
}
