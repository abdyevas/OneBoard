package com.oneboard.vacancy.source;

import java.util.List;

/**
 * A pluggable job board. Every Spring bean implementing this interface is picked up by the aggregator,
 * so adding a new board = adding one class.
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

    /**
     * Fetch current vacancies matching the query (null/blank = whatever the board returns by default).
     * Implementations should throw on transport errors; the aggregator isolates failures per source.
     */
    List<FetchedVacancy> fetch(String query);
}
