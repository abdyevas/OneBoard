package com.oneboard.aggregation;

import java.util.List;
import java.util.Set;

import org.springframework.web.bind.annotation.*;

import com.oneboard.aggregation.AggregationService.RefreshReport;
import com.oneboard.aggregation.AggregationService.SourceInfo;

@RestController
@RequestMapping("/api/aggregation")
public class AggregationController {

    private final AggregationService service;

    public AggregationController(AggregationService service) {
        this.service = service;
    }

    @GetMapping("/sources")
    public List<SourceInfo> sources() {
        return service.sources();
    }

    public record RefreshRequest(String query, Set<String> sources) {}

    /** Pull fresh vacancies from job boards. Synchronous: returns a per-source report. */
    @PostMapping("/refresh")
    public RefreshReport refresh(@RequestBody(required = false) RefreshRequest request) {
        String query = request == null ? null : request.query();
        Set<String> sources = request == null || request.sources() == null ? Set.of() : request.sources();
        return service.refresh(query, sources);
    }
}
