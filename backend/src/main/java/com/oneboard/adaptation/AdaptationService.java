package com.oneboard.adaptation;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneboard.adaptation.SkillExtractor.SkillMatch;
import com.oneboard.common.NotFoundException;
import com.oneboard.cv.Cv;
import com.oneboard.cv.CvService;
import com.oneboard.vacancy.Vacancy;
import com.oneboard.vacancy.VacancyService;

@Service
public class AdaptationService {

    /** How many most recent vacancies are scanned when ranking vacancies for a CV. */
    private static final int RANKING_WINDOW = 1000;

    private final CvService cvs;
    private final VacancyService vacancies;
    private final SkillExtractor skills;
    private final CvAdaptationRepository repository;
    private final Map<String, CvAdapter> adapters;

    public AdaptationService(CvService cvs, VacancyService vacancies, SkillExtractor skills,
                             CvAdaptationRepository repository, List<CvAdapter> adapters) {
        this.cvs = cvs;
        this.vacancies = vacancies;
        this.skills = skills;
        this.repository = repository;
        this.adapters = adapters.stream().collect(Collectors.toMap(CvAdapter::id, Function.identity()));
    }

    public record MethodInfo(String id, boolean available) {}

    public List<MethodInfo> methods() {
        return adapters.values().stream()
                .map(a -> new MethodInfo(a.id(), a.available()))
                .sorted(Comparator.comparing(MethodInfo::id))
                .toList();
    }

    public SkillMatch match(Cv cv, Vacancy v) {
        return skills.match(cv.getContent(), v.getDescription(), v.getTags(), v.getTitle());
    }

    @Transactional(readOnly = true)
    public SkillMatch match(Long cvId, Long vacancyId) {
        return match(cvs.get(cvId), vacancies.get(vacancyId));
    }

    /**
     * Note: not wrapped in one transaction on purpose — the LLM call can take a while and should not hold a
     * DB connection.
     */
    public CvAdaptation adapt(Long cvId, Long vacancyId, String method) {
        Cv cv = cvs.get(cvId);
        Vacancy vacancy = vacancies.get(vacancyId);
        CvAdapter adapter = pick(method);
        SkillMatch match = match(cv, vacancy);
        CvAdapter.Result result = adapter.adapt(cv, vacancy, match);
        return repository.save(new CvAdaptation(cv.getId(), cv.getTitle(), vacancy.getId(), vacancy.getTitle(),
                vacancy.getCompany(), adapter.id(), result.model(), match.score(), match.matched(), match.missing(),
                result.notes(), result.content()));
    }

    private CvAdapter pick(String method) {
        if (method == null || method.isBlank() || method.equals("auto")) {
            CvAdapter claude = adapters.get("claude");
            return claude != null && claude.available() ? claude : adapters.get("keyword");
        }
        CvAdapter a = adapters.get(method);
        if (a == null) throw new IllegalArgumentException("Unknown method: " + method + ", expected one of " + adapters.keySet());
        if (!a.available()) throw new IllegalStateException("Method '" + method + "' is not configured");
        return a;
    }

    @Transactional(readOnly = true)
    public List<CvAdaptation> list(Long cvId, Long vacancyId) {
        return repository.find(cvId, vacancyId);
    }

    @Transactional(readOnly = true)
    public CvAdaptation get(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Adaptation", id));
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new NotFoundException("Adaptation", id);
        repository.deleteById(id);
    }

    public record RankedVacancy(Long vacancyId, String title, String company, String source, int score,
                                List<String> matched, List<String> missing) {}

    /** Best-matching vacancies for a CV among the most recent ones. */
    @Transactional(readOnly = true)
    public List<RankedVacancy> rankVacancies(Long cvId, String query, int limit) {
        Cv cv = cvs.get(cvId);
        var page = vacancies.search(query, null, null,
                PageRequest.of(0, RANKING_WINDOW, Sort.by(Sort.Order.desc("publishedAt"))));
        return page.getContent().stream()
                .map(v -> {
                    SkillMatch m = match(cv, v);
                    return new RankedVacancy(v.getId(), v.getTitle(), v.getCompany(), v.getSource(), m.score(),
                            m.matched(), m.missing());
                })
                .filter(r -> r.score() > 0)
                .sorted(Comparator.comparingInt(RankedVacancy::score).reversed()
                        .thenComparing(r -> -r.matched().size()))
                .limit(Math.clamp(limit, 1, 200))
                .toList();
    }
}
