package com.oneboard.vacancy;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.oneboard.common.PageResponse;
import com.oneboard.vacancy.VacancyDtos.CreateVacancyRequest;
import com.oneboard.vacancy.VacancyDtos.VacancyDetails;
import com.oneboard.vacancy.VacancyDtos.VacancySummary;

@RestController
@RequestMapping("/api/vacancies")
public class VacancyController {

    private final VacancyService service;

    public VacancyController(VacancyService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<VacancySummary> search(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) String source,
                                               @RequestParam(required = false) Boolean remote,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by(Sort.Order.desc("publishedAt"), Sort.Order.desc("id")));
        return PageResponse.of(service.search(q, source, remote, pageable), VacancySummary::of);
    }

    @GetMapping("/stats")
    public Map<String, Long> stats() {
        return service.stats();
    }

    @GetMapping("/{id}")
    public VacancyDetails get(@PathVariable Long id) {
        return VacancyDetails.of(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VacancyDetails create(@Valid @RequestBody CreateVacancyRequest request) {
        return VacancyDetails.of(service.createManual(request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
