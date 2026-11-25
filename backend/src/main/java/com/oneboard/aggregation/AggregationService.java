package com.oneboard.aggregation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.oneboard.vacancy.VacancyService;
import com.oneboard.vacancy.source.FetchedVacancy;
import com.oneboard.vacancy.source.VacancySource;

/** Pulls vacancies from every registered {@link VacancySource} and upserts them into the DB. */
@Service
public class AggregationService {

    private static final Logger log = LoggerFactory.getLogger(AggregationService.class);

    private final List<VacancySource> sources;
    private final VacancyService vacancies;
    private final AggregationProperties props;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public AggregationService(List<VacancySource> sources, VacancyService vacancies, AggregationProperties props) {
        this.sources = sources;
        this.vacancies = vacancies;
        this.props = props;
    }

    public record SourceInfo(String id, String name, boolean enabled) {}

    public record SourceResult(String source, int fetched, int created, int updated, long durationMs, String error) {}

    public record RefreshReport(String query, Instant startedAt, List<SourceResult> results) {}

    public List<SourceInfo> sources() {
        return sources.stream().map(s -> new SourceInfo(s.id(), s.displayName(), s.enabled())).toList();
    }

    /**
     * @param query     search text passed to each source (may be blank)
     * @param sourceIds restrict to these sources; empty = all
     */
    public RefreshReport refresh(String query, Set<String> sourceIds) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A refresh is already running, try again later");
        }
        try {
            Instant started = Instant.now();
            List<SourceResult> results = new ArrayList<>();
            for (VacancySource source : sources) {
                boolean requested = sourceIds.contains(source.id());
                if (!sourceIds.isEmpty() && !requested) continue;
                if (!source.enabled()) {
                    // silently skip unconfigured sources in "refresh all"; explain when asked for explicitly
                    if (requested) {
                        results.add(new SourceResult(source.id(), 0, 0, 0, 0, "not configured (API key missing)"));
                    }
                    continue;
                }
                results.add(refreshOne(source, query));
            }
            return new RefreshReport(query, started, results);
        } finally {
            running.set(false);
        }
    }

    private SourceResult refreshOne(VacancySource source, String query) {
        long t0 = System.nanoTime();
        try {
            List<FetchedVacancy> fetched = source.fetch(query);
            int created = 0;
            int updated = 0;
            for (FetchedVacancy f : fetched) {
                if (f.externalId() == null || f.title() == null) continue;
                if (vacancies.upsert(source.id(), f)) created++; else updated++;
            }
            long ms = Duration.ofNanos(System.nanoTime() - t0).toMillis();
            log.info("Source {}: fetched={}, created={}, updated={} in {} ms", source.id(), fetched.size(), created, updated, ms);
            return new SourceResult(source.id(), fetched.size(), created, updated, ms, null);
        } catch (Exception e) {
            long ms = Duration.ofNanos(System.nanoTime() - t0).toMillis();
            log.warn("Source {} failed: {}", source.id(), e.toString());
            return new SourceResult(source.id(), 0, 0, 0, ms, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @Scheduled(cron = "${oneboard.aggregation.cron:0 0 */6 * * *}")
    void scheduledRefresh() {
        if (!props.scheduleEnabled()) return;
        for (String q : props.defaultQueries()) {
            try {
                refresh(q, Set.of());
            } catch (IllegalStateException busy) {
                log.info("Skipping scheduled refresh: {}", busy.getMessage());
            }
        }
    }
}
