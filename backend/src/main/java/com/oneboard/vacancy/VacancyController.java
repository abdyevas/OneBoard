package com.oneboard.vacancy;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.oneboard.application.JobApplicationService;
import com.oneboard.common.PageResponse;
import com.oneboard.vacancy.VacancyDtos.CreateVacancyRequest;
import com.oneboard.vacancy.VacancyDtos.VacancyDetails;
import com.oneboard.vacancy.VacancyDtos.VacancySummary;

@RestController
@RequestMapping("/api/vacancies")
public class VacancyController {

    private final VacancyService service;
    private final JobApplicationService applications;

    public VacancyController(VacancyService service, JobApplicationService applications) {
        this.service = service;
        this.applications = applications;
    }

    @GetMapping
    public PageResponse<VacancySummary> search(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) String source,
                                               @RequestParam(required = false) Boolean remote,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id")));
        var result = service.search(q, source, remote, pageable);
        var apps = applications.forVacancies(result.getContent().stream().map(Vacancy::getId).toList());
        return PageResponse.of(result, v -> VacancySummary.of(v, apps.get(v.getId())));
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        return service.stats();
    }

    @GetMapping("/{id}")
    public VacancyDetails get(@PathVariable Long id) {
        return VacancyDetails.of(service.get(id), applications.forVacancy(id).orElse(null));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VacancyDetails create(@Valid @RequestBody CreateVacancyRequest request) {
        return VacancyDetails.of(service.createManual(request), null);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
        applications.remove(id);
    }
}
