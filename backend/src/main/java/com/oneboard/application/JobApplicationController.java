package com.oneboard.application;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/applications")
public class JobApplicationController {

    private final JobApplicationService service;

    public JobApplicationController(JobApplicationService service) {
        this.service = service;
    }

    public record ApplicationRequest(@NotNull ApplicationStatus status, @Size(max = 2000) String note) {}

    public record ApplicationResponse(Long vacancyId, ApplicationStatus status, String note,
                                      Instant appliedAt, Instant updatedAt) {
        public static ApplicationResponse of(JobApplication a) {
            return new ApplicationResponse(a.getVacancyId(), a.getStatus(), a.getNote(), a.getAppliedAt(), a.getUpdatedAt());
        }
    }

    @GetMapping
    public List<ApplicationResponse> list() {
        return service.list().stream().map(ApplicationResponse::of).toList();
    }

    /** Mark that the user applied to a vacancy, or move it to another status. */
    @PutMapping("/{vacancyId}")
    public ApplicationResponse set(@PathVariable Long vacancyId, @Valid @RequestBody ApplicationRequest req) {
        return ApplicationResponse.of(service.set(vacancyId, req.status(), req.note()));
    }

    @DeleteMapping("/{vacancyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long vacancyId) {
        service.remove(vacancyId);
    }
}
