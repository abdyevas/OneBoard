package com.oneboard.vacancy.source;

import java.util.List;
import java.util.Optional;

import com.oneboard.vacancy.Vacancy;

/**
 * A pluggable job board. Every Spring bean implementing this interface is picked up by the aggregator,
 * so adding a new board = adding one class. Only boards whose terms allow it may be added
 * (see README, "Азербайджанские площадки").
 */
public interface VacancySource {

    /** Stable identifier stored in {@code vacancy.source}. */
    String id();

    /** Human-readable name for the UI. */
    String displayName();

    /** False when the source needs configuration (e.g. an API key) that is missing; it is then skipped. */
    default boolean enabled() {
        return true;
    }

    /** Whether the search query is applied by the board itself (otherwise we fetch the newest and filter locally). */
    default boolean supportsQuery() {
        return true;
    }

    /**
     * Fetch current vacancies matching the query (null/blank = whatever the board returns by default).
     * May return summaries without a description; those are completed later via {@link #fetchDetails}.
     * Implementations should throw on transport errors; the aggregator isolates failures per source.
     */
    List<FetchedVacancy> fetch(String query);

    /**
     * Loads the full vacancy for a stored summary. Called in the background, one at a time, so it must
     * respect the site's crawl delay. Empty = this source has no separate details step.
     */
    default Optional<FetchedVacancy> fetchDetails(Vacancy summary) {
        return Optional.empty();
    }

    /** Host used for crawl-delay accounting of {@link #fetchDetails}; null if details aren't supported. */
    default String detailsHost() {
        return null;
    }

    default java.time.Duration detailsDelay() {
        return java.time.Duration.ofSeconds(2);
    }
}
