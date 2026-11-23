package com.oneboard.vacancy.source;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.oneboard.common.Html;

/**
 * Remotive public API (remote jobs, no key required).
 * Docs: https://remotive.com/api-documentation — they ask to keep request volume low and link back to the job URL.
 */
@Component
public class RemotiveSource implements VacancySource {

    private static final String URL = "https://remotive.com/api/remote-jobs";

    private final RestClient http;

    public RemotiveSource(@Qualifier("sourceRestClient") RestClient http) {
        this.http = http;
    }

    @Override public String id() { return "remotive"; }
    @Override public String displayName() { return "Remotive"; }

    @Override
    public List<FetchedVacancy> fetch(String query) {
        Response resp = http.get()
                .uri(URL + "?limit={limit}&search={q}", 100, query == null ? "" : query)
                .retrieve()
                .body(Response.class);
        if (resp == null || resp.jobs() == null) return List.of();

        List<FetchedVacancy> result = new ArrayList<>();
        for (Job j : resp.jobs()) {
            List<String> tags = new ArrayList<>();
            if (j.category() != null && !j.category().isBlank()) tags.add(j.category());
            if (j.tags() != null) tags.addAll(j.tags());
            result.add(new FetchedVacancy(
                    String.valueOf(j.id()),
                    j.title(),
                    j.companyName(),
                    j.candidateRequiredLocation(),
                    true,
                    j.jobType(),
                    blankToNull(j.salary()),
                    Html.toText(j.description()),
                    j.url(),
                    tags,
                    parseDate(j.publicationDate())));
        }
        return result;
    }

    private static Instant parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            try { return Instant.parse(s); } catch (DateTimeParseException ignored) { return null; }
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(List<Job> jobs) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Job(long id,
               String url,
               String title,
               @JsonProperty("company_name") String companyName,
               String category,
               List<String> tags,
               @JsonProperty("job_type") String jobType,
               @JsonProperty("publication_date") String publicationDate,
               @JsonProperty("candidate_required_location") String candidateRequiredLocation,
               String salary,
               String description) {}
}
