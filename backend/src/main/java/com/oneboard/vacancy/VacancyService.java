package com.oneboard.vacancy;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneboard.application.JobApplicationRepository;
import com.oneboard.common.NotFoundException;
import com.oneboard.vacancy.VacancyDtos.CreateVacancyRequest;
import com.oneboard.vacancy.source.FetchedVacancy;

@Service
public class VacancyService {

    public static final String MANUAL_SOURCE = "manual";

    private final VacancyRepository repository;
    private final JobApplicationRepository applications;

    public VacancyService(VacancyRepository repository, JobApplicationRepository applications) {
        this.repository = repository;
        this.applications = applications;
    }

    @Transactional(readOnly = true)
    public Page<Vacancy> search(String query, String source, Boolean remote, Pageable pageable) {
        String like = (query == null || query.isBlank()) ? null : "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
        String src = (source == null || source.isBlank()) ? null : source;
        return repository.search(like, src, remote, pageable);
    }

    @Transactional(readOnly = true)
    public Vacancy get(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Vacancy", id));
    }

    @Transactional
    public Vacancy createManual(CreateVacancyRequest req) {
        Vacancy v = new Vacancy(MANUAL_SOURCE, UUID.randomUUID().toString());
        v.setTitle(req.title());
        v.setCompany(req.company());
        v.setLocation(req.location());
        v.setRemote(req.remote());
        v.setSalary(req.salary());
        v.setDescription(req.description());
        v.setUrl(req.url());
        v.setTags(req.tags());
        Instant now = Instant.now();
        v.setPublishedAt(now);
        v.setFetchedAt(now);
        return repository.save(v);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) throw new NotFoundException("Vacancy", id);
        repository.deleteById(id);
    }

    /** Insert or update a vacancy coming from an external source. Returns true if it was new. */
    @Transactional
    public boolean upsert(String source, FetchedVacancy f) {
        var existing = repository.findBySourceAndExternalId(source, f.externalId());
        Vacancy v = existing.orElseGet(() -> new Vacancy(source, f.externalId()));
        apply(v, f);
        repository.save(v);
        return existing.isEmpty();
    }

    /** Fill in the full details of a stored summary (from {@code VacancySource.fetchDetails}). */
    @Transactional
    public void completeDetails(Long id, FetchedVacancy f) {
        repository.findById(id).ifPresent(v -> {
            apply(v, f);
            if (v.getDescription() == null) v.setDescription(""); // mark as done even if the board has no text
        });
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Vacancy> nextWithoutDetails(String source, Collection<Long> excludedIds) {
        // "NOT IN ()" is invalid SQL, so exclude an impossible id when the set is empty
        Collection<Long> excluded = excludedIds.isEmpty() ? List.of(-1L) : excludedIds;
        return repository.findFirstBySourceAndDescriptionIsNullAndIdNotInOrderByPublishedAtDesc(source, excluded);
    }

    /**
     * Copies non-empty values only: a list refresh (summaries) must not wipe the description,
     * deadline etc. that were loaded earlier from the details page.
     */
    private static void apply(Vacancy v, FetchedVacancy f) {
        if (f.title() != null) v.setTitle(truncate(f.title(), 500));
        if (f.company() != null) v.setCompany(truncate(f.company(), 255));
        if (f.location() != null) v.setLocation(truncate(f.location(), 255));
        v.setRemote(v.isRemote() || f.remote());
        if (f.employmentType() != null) v.setEmploymentType(truncate(f.employmentType(), 100));
        if (f.salary() != null) v.setSalary(truncate(f.salary(), 255));
        if (f.description() != null) v.setDescription(truncate(f.description(), 100_000));
        if (f.url() != null) v.setUrl(truncate(f.url(), 1000));
        if (f.tags() != null && !f.tags().isEmpty()) {
            v.setTags(f.tags().stream().filter(t -> t != null && !t.isBlank()).map(t -> truncate(t, 100)).distinct().toList());
        }
        if (f.publishedAt() != null) v.setPublishedAt(f.publishedAt());
        if (f.deadline() != null) v.setDeadlineAt(f.deadline());
        v.setFetchedAt(Instant.now());
    }

    /**
     * Removes vacancies that came from sources which are no longer configured (e.g. a job board was
     * dropped). Manually added vacancies are always kept. Returns how many were removed.
     */
    @Transactional
    public int purgeSourcesOtherThan(Collection<String> activeSources) {
        Set<String> keep = new HashSet<>(activeSources);
        keep.add(MANUAL_SOURCE);
        List<Vacancy> stale = repository.findBySourceNotIn(keep);
        if (stale.isEmpty()) return 0;
        List<Long> ids = stale.stream().map(Vacancy::getId).toList();
        applications.deleteAll(applications.findByVacancyIdIn(ids));
        repository.deleteAll(stale);
        return stale.size();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> stats() {
        Map<String, Long> result = new TreeMap<>();
        for (Object[] row : repository.countBySource()) {
            result.put((String) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
