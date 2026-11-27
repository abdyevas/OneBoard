package com.oneboard.vacancy.source;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.oneboard.common.Html;
import com.oneboard.vacancy.Vacancy;
import com.oneboard.vacancy.VacancyRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * smartjob.az — reads the public job sitemap (made for crawlers) and the schema.org {@code JobPosting}
 * data embedded in each vacancy page (the data meant for search engines / Google Jobs).
 * <p>
 * Rules we follow (checked Nov 2026): the terms of use don't restrict reading the site; robots.txt
 * allows the vacancy pages and disallows {@code /api/}, which we never call. No crawl delay is set,
 * so we use a conservative 2 s between requests and only fetch new or updated vacancies.
 * The site's search runs in the browser only, so we take the newest vacancies and search locally.
 */
@Component
public class SmartjobAzSource implements VacancySource {

    private static final Logger log = LoggerFactory.getLogger(SmartjobAzSource.class);

    static final String HOST = "smartjob.az";
    private static final String SITEMAP = "https://smartjob.az/sitemaps/jobs/1";
    private static final Duration DELAY = Duration.ofSeconds(2);
    /** Newest sitemap entries looked at per refresh. */
    private static final int SCAN_LIMIT = 60;
    /** Max vacancy pages downloaded per refresh (keeps one refresh under ~1 minute). */
    private static final int FETCH_LIMIT = 25;

    private static final Pattern URL_ENTRY = Pattern.compile(
            "<url>\\s*<loc>([^<]+)</loc>\\s*(?:<lastmod>([^<]+)</lastmod>)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern LD_JSON = Pattern.compile(
            "<script type=\"application/ld\\+json\"[^>]*>(.*?)</script>", Pattern.DOTALL);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final RestClient http;
    private final PoliteHttp polite;
    private final VacancyRepository vacancies;

    public SmartjobAzSource(@Qualifier("sourceRestClient") RestClient http, PoliteHttp polite,
                            VacancyRepository vacancies) {
        this.http = http;
        this.polite = polite;
        this.vacancies = vacancies;
    }

    @Override public String id() { return "smartjob"; }
    @Override public String displayName() { return "smartjob.az"; }
    @Override public boolean supportsQuery() { return false; }

    record SitemapEntry(String url, Instant lastmod) {}

    @Override
    public List<FetchedVacancy> fetch(String query) {
        polite.await(HOST, DELAY);
        String xml = getText(SITEMAP);
        List<SitemapEntry> entries = parseSitemap(xml).stream()
                .sorted(Comparator.comparing(SitemapEntry::lastmod, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(SCAN_LIMIT)
                .toList();

        List<FetchedVacancy> result = new ArrayList<>();
        int fetched = 0;
        for (SitemapEntry e : entries) {
            if (fetched >= FETCH_LIMIT) break;
            if (isUpToDate(e)) continue;
            fetched++;
            try {
                polite.await(HOST, DELAY);
                parseJobPosting(getText(e.url()), e).ifPresent(result::add);
            } catch (RuntimeException ex) {
                log.warn("smartjob.az: skipping {}: {}", e.url(), ex.toString());
            }
        }
        return result;
    }

    /**
     * Reads a page as text. Without an explicit charset Spring would assume ISO-8859-1 and garble
     * Azerbaijani letters (ə, ı, ş), so we default to UTF-8.
     */
    private String getText(String url) {
        ResponseEntity<byte[]> resp = http.get().uri(url).accept(MediaType.ALL).retrieve().toEntity(byte[].class);
        byte[] body = resp.getBody();
        if (body == null) return null;
        Charset charset = Optional.ofNullable(resp.getHeaders().getContentType())
                .map(MediaType::getCharset).orElse(StandardCharsets.UTF_8);
        return new String(body, charset);
    }

    /** Already stored and not changed on the site since we last read it. */
    private boolean isUpToDate(SitemapEntry e) {
        String id = idFromUrl(e.url());
        return vacancies.findBySourceAndExternalId(id(), id)
                .map(v -> e.lastmod() != null && v.getFetchedAt() != null && !e.lastmod().isAfter(v.getFetchedAt()))
                .orElse(false);
    }

    static List<SitemapEntry> parseSitemap(String xml) {
        if (xml == null) return List.of();
        List<SitemapEntry> out = new ArrayList<>();
        Matcher m = URL_ENTRY.matcher(xml);
        while (m.find()) {
            String url = m.group(1).strip();
            if (!url.contains("/vakansiyalar/")) continue;
            out.add(new SitemapEntry(url, parseDate(m.group(2))));
        }
        return out;
    }

    /** The slug is stable per vacancy, e.g. "satici-kassir-elmler-nerimanov-36612". */
    static String idFromUrl(String url) {
        String s = url.replaceFirst("[?#].*$", "");
        return s.substring(s.lastIndexOf('/') + 1);
    }

    static Optional<FetchedVacancy> parseJobPosting(String html, SitemapEntry entry) {
        if (html == null) return Optional.empty();
        Matcher m = LD_JSON.matcher(html);
        while (m.find()) {
            JsonNode root;
            try {
                root = JSON.readTree(m.group(1));
            } catch (RuntimeException e) {
                continue;
            }
            for (JsonNode node : candidates(root)) {
                if ("JobPosting".equals(text(node.path("@type")))) {
                    return Optional.ofNullable(toVacancy(node, entry));
                }
            }
        }
        return Optional.empty();
    }

    private static List<JsonNode> candidates(JsonNode root) {
        List<JsonNode> list = new ArrayList<>();
        if (root.isArray()) root.forEach(list::add);
        else if (root.has("@graph")) root.path("@graph").forEach(list::add);
        else list.add(root);
        return list;
    }

    private static FetchedVacancy toVacancy(JsonNode p, SitemapEntry entry) {
        String title = text(p.path("title"));
        if (title == null) return null;
        Instant validThrough = parseDate(text(p.path("validThrough")));
        if (validThrough != null && validThrough.isBefore(Instant.now())) return null; // expired

        JsonNode address = p.path("jobLocation").path("address");
        String city = text(address.path("addressLocality"));
        boolean remote = "TELECOMMUTE".equalsIgnoreCase(text(p.path("jobLocationType")));

        List<String> tags = new ArrayList<>();
        for (String field : List.of("occupationalCategory", "industry")) {
            String t = text(p.path(field));
            if (t != null && !t.equalsIgnoreCase("Digər")) tags.add(t);
        }

        Instant posted = parseDate(text(p.path("datePosted")));
        // the sitemap's lastmod shows when the listing was last renewed; prefer it for "freshness"
        Instant published = entry.lastmod() != null && (posted == null || entry.lastmod().isAfter(posted))
                ? entry.lastmod() : posted;

        return new FetchedVacancy(
                idFromUrl(entry.url()),
                title.strip(),
                text(p.path("hiringOrganization").path("name")),
                city,
                remote,
                employmentType(text(p.path("employmentType"))),
                salary(p.path("baseSalary")),
                Html.toText(text(p.path("description"))),
                entry.url(),
                tags,
                published,
                validThrough);
    }

    private static String employmentType(String schemaType) {
        if (schemaType == null) return null;
        return switch (schemaType.toUpperCase(Locale.ROOT)) {
            case "FULL_TIME" -> "Tam ştat";
            case "PART_TIME" -> "Yarım ştat";
            case "CONTRACTOR" -> "Müqavilə";
            case "TEMPORARY" -> "Müvəqqəti";
            case "INTERN" -> "Təcrübə";
            case "VOLUNTEER" -> "Könüllü";
            default -> schemaType;
        };
    }

    /** schema.org MonetaryAmount -> "800–1200 AZN" */
    private static String salary(JsonNode s) {
        if (s.isMissingNode() || s.isNull()) return null;
        String currency = text(s.path("currency"));
        JsonNode v = s.path("value");
        String min = text(v.path("minValue"));
        String max = text(v.path("maxValue"));
        String single = v.isValueNode() ? text(v) : text(v.path("value"));
        String amount = min != null && max != null ? min + "–" + max : (min != null ? "от " + min : (max != null ? "до " + max : single));
        if (amount == null) return null;
        return currency == null ? amount : amount + " " + currency;
    }

    private static String text(JsonNode n) {
        if (n == null || n.isMissingNode() || n.isNull()) return null;
        String s = n.isValueNode() ? n.asString() : null;
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static Instant parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (DateTimeParseException e) {
            try { return Instant.parse(s); } catch (DateTimeParseException ignored) { return null; }
        }
    }
}
