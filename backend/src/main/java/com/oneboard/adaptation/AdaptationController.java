package com.oneboard.adaptation;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import com.oneboard.adaptation.AdaptationService.MethodInfo;
import com.oneboard.adaptation.AdaptationService.RankedVacancy;
import com.oneboard.adaptation.SkillExtractor.SkillMatch;

@RestController
@RequestMapping("/api")
public class AdaptationController {

    private final AdaptationService service;

    public AdaptationController(AdaptationService service) {
        this.service = service;
    }

    public record AdaptRequest(@NotNull Long cvId, @NotNull Long vacancyId, String method) {}

    public record AdaptationResponse(Long id, Long cvId, String cvTitle, Long vacancyId, String vacancyTitle,
                                     String company, String method, String model, int matchScore,
                                     List<String> matchedSkills, List<String> missingSkills, List<String> notes,
                                     String content, Instant createdAt) {
        static AdaptationResponse of(CvAdaptation a) {
            return new AdaptationResponse(a.getId(), a.getCvId(), a.getCvTitle(), a.getVacancyId(),
                    a.getVacancyTitle(), a.getCompany(), a.getMethod(), a.getModel(), a.getMatchScore(),
                    a.getMatchedSkills(), a.getMissingSkills(), a.getNotes(), a.getContent(), a.getCreatedAt());
        }
    }

    @GetMapping("/adaptations/methods")
    public List<MethodInfo> methods() {
        return service.methods();
    }

    /** Quick skill match without generating anything. */
    @GetMapping("/match")
    public SkillMatch match(@RequestParam Long cvId, @RequestParam Long vacancyId) {
        return service.match(cvId, vacancyId);
    }

    /** Vacancies that fit a CV best (optionally narrowed by a text query). */
    @GetMapping("/cvs/{cvId}/matches")
    public List<RankedVacancy> matches(@PathVariable Long cvId,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "20") int limit) {
        return service.rankVacancies(cvId, q, limit);
    }

    @PostMapping("/adaptations")
    @ResponseStatus(HttpStatus.CREATED)
    public AdaptationResponse adapt(@Valid @RequestBody AdaptRequest req) {
        return AdaptationResponse.of(service.adapt(req.cvId(), req.vacancyId(), req.method()));
    }

    @GetMapping("/adaptations")
    public List<AdaptationResponse> list(@RequestParam(required = false) Long cvId,
                                         @RequestParam(required = false) Long vacancyId) {
        return service.list(cvId, vacancyId).stream().map(AdaptationResponse::of).toList();
    }

    @GetMapping("/adaptations/{id}")
    public AdaptationResponse get(@PathVariable Long id) {
        return AdaptationResponse.of(service.get(id));
    }

    @DeleteMapping("/adaptations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
