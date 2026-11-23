package com.oneboard.vacancy;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class VacancyDtos {
    private VacancyDtos() {}

    public record VacancySummary(Long id, String source, String title, String company, String location,
                                 boolean remote, String salary, List<String> tags, Instant publishedAt, String url) {
        static VacancySummary of(Vacancy v) {
            return new VacancySummary(v.getId(), v.getSource(), v.getTitle(), v.getCompany(), v.getLocation(),
                    v.isRemote(), v.getSalary(), List.copyOf(v.getTags()), v.getPublishedAt(), v.getUrl());
        }
    }

    public record VacancyDetails(Long id, String source, String externalId, String title, String company,
                                 String location, boolean remote, String employmentType, String salary,
                                 String description, String url, List<String> tags,
                                 Instant publishedAt, Instant fetchedAt) {
        static VacancyDetails of(Vacancy v) {
            return new VacancyDetails(v.getId(), v.getSource(), v.getExternalId(), v.getTitle(), v.getCompany(),
                    v.getLocation(), v.isRemote(), v.getEmploymentType(), v.getSalary(), v.getDescription(),
                    v.getUrl(), List.copyOf(v.getTags()), v.getPublishedAt(), v.getFetchedAt());
        }
    }

    /** Manually added vacancy (e.g. pasted by the user from a site we don't aggregate). */
    public record CreateVacancyRequest(@NotBlank @Size(max = 500) String title,
                                       @Size(max = 255) String company,
                                       @Size(max = 255) String location,
                                       boolean remote,
                                       @Size(max = 255) String salary,
                                       @NotBlank @Size(max = 100_000) String description,
                                       @Size(max = 1000) String url,
                                       List<String> tags) {}
}
