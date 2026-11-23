package com.oneboard.vacancy.source;

import java.time.Instant;
import java.util.List;

/** Source-agnostic vacancy as returned by a {@link VacancySource}; description is plain text. */
public record FetchedVacancy(
        String externalId,
        String title,
        String company,
        String location,
        boolean remote,
        String employmentType,
        String salary,
        String description,
        String url,
        List<String> tags,
        Instant publishedAt) {
}
