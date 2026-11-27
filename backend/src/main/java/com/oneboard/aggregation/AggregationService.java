package com.oneboard.aggregation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.oneboard.vacancy.VacancyService;
import com.oneboard.vacancy.source.FetchedVacancy;
import com.oneboard.vacancy.source.VacancySource;

/**
 * Pulls vacancies from every registered {@link VacancySource} and upserts them into the DB.
 * Sources wait between requests to honour each site's crawl delay, so a refresh can take a minute;
 * it runs in the background and the UI polls {@link #status()}.
 */
@Service
public class AggregationService {

    private static final Logger log = LoggerFactory.getLogger(AggregationService.class);

    private final List<VacancySource> sources;
    private final VacancyService vacancies;
    private final AggregationProperties props;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "aggregation");
        t.setDaemon(true);
        return t;
    });

    private volatile RefreshStatus lastStatus = RefreshStatus.idle();

    public AggregationService(List<VacancySource> sources, VacancyService vacancies, AggregationProperties props) {
        this.sources = sources;
        this.vacancies = vacancies;
        this.props = props;
    }

    public record SourceInfo(String id, String name, boolean enabled, boolean supportsQuery) {}

    public record SourceResult(String source, int fetched, int created, int updated, long durationMs, String error) {}

    public record RefreshReport(String query, Instant startedAt, List<SourceResult> results) {}

    /** Progress of the current (or last) background refresh. */
    public record RefreshStatus(boolean running, String query, Instant startedAt, Instant finishedAt,
                                String currentSource, List<SourceResult> results) {
        static RefreshStatus idle() {
            return new RefreshStatus(false, null, null, null, null, List.of());
        }
    }

    public List<SourceInfo> sources() {
        return sources.stream().map(s -> new SourceInfo(s.id(), s.displayName(), s.enabled(), s.supportsQuery())).toList();
    }

    public RefreshStatus status() {
        return lastStatus;
    }

    /** Starts a refresh in the background; returns immediately with the initial status. */
    public RefreshStatus start(String query, Set<String> sourceIds) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A refresh is already running, try again later");
        }
        lastStatus = new RefreshStatus(true, query, Instant.now(), null, null, List.of());
        try {
            executor.submit(() -> {
                try {
                    runRefresh(query, sourceIds);
                } catch (RuntimeException e) {
                    log.error("Refresh failed", e);
                } finally {
                    running.set(false);
                }
            });
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
        return lastStatus;
    }

    /** Synchronous refresh (used by the scheduler and tests). */
    public RefreshReport refresh(String query, Set<String> sourceIds) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A refresh is already running, try again later");
        }
        try {
            return runRefresh(query, sourceIds);
        } finally {
            running.set(false);
        }
    }

    private RefreshReport runRefresh(String query, Set<String> sourceIds) {
        Instant started = Instant.now();
        List<SourceResult> results = new ArrayList<>();
        lastStatus = new RefreshStatus(true, query, started, null, null, List.copyOf(results));
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
            lastStatus = new RefreshStatus(true, query, started, null, source.id(), List.copyOf(results));
            results.add(refreshOne(source, query));
        }
        lastStatus = new RefreshStatus(false, query, started, Instant.now(), null, List.copyOf(results));
        return new RefreshReport(query, started, results);
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

    /** Drop vacancies of job boards that were removed from the app (only Azerbaijani sources are kept). */
    @EventListener(ApplicationReadyEvent.class)
    void purgeRemovedSources() {
        int removed = vacancies.purgeSourcesOtherThan(sources.stream().map(VacancySource::id).toList());
        if (removed > 0) log.info("Removed {} vacancies from sources that are no longer configured", removed);
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

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
