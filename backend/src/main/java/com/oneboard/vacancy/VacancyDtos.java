package com.oneboard.vacancy;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.oneboard.application.JobApplication;
import com.oneboard.application.JobApplicationController.ApplicationResponse;

public final class VacancyDtos {
    private VacancyDtos() {}

    public record VacancySummary(Long id, String source, String title, String company, String location,
                                 boolean remote, String salary, List<String> tags, Instant publishedAt, String url,
                                 String applicationStatus) {
        static VacancySummary of(Vacancy v, JobApplication application) {
            return new VacancySummary(v.getId(), v.getSource(), v.getTitle(), v.getCompany(), v.getLocation(),
                    v.isRemote(), v.getSalary(), List.copyOf(v.getTags()), v.getPublishedAt(), v.getUrl(),
                    application == null ? null : application.getStatus().name());
        }
    }

    public record VacancyDetails(Long id, String source, String externalId, String title, String company,
                                 String location, boolean remote, String employmentType, String salary,
                                 String description, String url, List<String> tags,
                                 Instant publishedAt, Instant fetchedAt, ApplicationResponse application) {
        static VacancyDetails of(Vacancy v, JobApplication application) {
            return new VacancyDetails(v.getId(), v.getSource(), v.getExternalId(), v.getTitle(), v.getCompany(),
                    v.getLocation(), v.isRemote(), v.getEmploymentType(), v.getSalary(), v.getDescription(),
                    v.getUrl(), List.copyOf(v.getTags()), v.getPublishedAt(), v.getFetchedAt(),
                    application == null ? null : ApplicationResponse.of(application));
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
