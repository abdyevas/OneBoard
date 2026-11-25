package com.oneboard.vacancy.source;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.oneboard.common.Html;

/**
 * Azerbaijani vacancies via the official Jooble partner REST API (az.jooble.org).
 * Docs: https://jooblehelpcenter.freshdesk.com/en/support/solutions/articles/60001448238-rest-api-documentation
 * <p>
 * Only enabled when {@code JOOBLE_API_KEY} is set. Each listing links back to the posting on Jooble.
 * The API returns a short snippet, not the full description.
 */
@Component
public class JoobleSource implements VacancySource {

    private static final Pattern REMOTE = Pattern.compile("remote|uzaqdan|udalen|удал[её]н", Pattern.CASE_INSENSITIVE);

    private final RestClient http;
    private final JoobleProperties props;

    public JoobleSource(@Qualifier("sourceRestClient") RestClient http, JoobleProperties props) {
        this.http = http;
        this.props = props;
    }

    @Override public String id() { return "jooble"; }
    @Override public String displayName() { return "Jooble (Azərbaycan)"; }
    @Override public boolean enabled() { return props.configured(); }

    @Override
    public List<FetchedVacancy> fetch(String query) {
        if (!enabled()) {
            throw new IllegalStateException("JOOBLE_API_KEY is not set");
        }
        List<FetchedVacancy> result = new ArrayList<>();
        for (int page = 1; page <= Math.max(1, props.maxPages()); page++) {
            Response resp;
            try {
                resp = http.post()
                        .uri(props.baseUrl() + "/api/{key}", props.apiKey())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of(
                                "keywords", query == null ? "" : query,
                                "location", props.location(),
                                "page", String.valueOf(page),
                                "ResultOnPage", String.valueOf(props.resultsPerPage())))
                        .retrieve()
                        .body(Response.class);
            } catch (HttpClientErrorException.Forbidden e) {
                throw new IllegalStateException("Jooble rejected the API key (403). Check JOOBLE_API_KEY and that it was issued for " + props.baseUrl());
            } catch (HttpClientErrorException.TooManyRequests e) {
                if (!result.isEmpty()) break; // keep what we already got
                throw new IllegalStateException("Jooble daily request limit reached (429); it resets at midnight UTC");
            }
            if (resp == null || resp.jobs() == null || resp.jobs().isEmpty()) break;
            for (Job j : resp.jobs()) {
                if (j.link() == null) continue;
                String title = Html.toText(j.title());
                String location = Html.toText(j.location());
                String snippet = Html.toText(j.snippet());
                result.add(new FetchedVacancy(
                        j.id() != null ? j.id() : j.link(),
                        title,
                        blankToNull(Html.toText(j.company())),
                        blankToNull(location),
                        REMOTE.matcher(title + " " + location + " " + snippet).find(),
                        blankToNull(j.type()),
                        blankToNull(j.salary()),
                        snippet,
                        j.link(),
                        List.of(),
                        parseDate(j.updated())));
            }
            if (resp.jobs().size() < props.resultsPerPage()) break;
        }
        return result;
    }

    /** Jooble returns local timestamps without an offset, e.g. "2023-09-15T12:55:35.3870000". */
    static Instant parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s.length() > 26 ? s.substring(0, 26) : s).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            try { return Instant.parse(s); } catch (DateTimeParseException ignored) { return null; }
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(Long totalCount, List<Job> jobs) {}

    /** Field types are loose on purpose: the docs and examples disagree on numbers vs strings. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Job(String id, String title, String location, String snippet, String salary, String source,
               String type, String link, String company, String updated) {}
}
