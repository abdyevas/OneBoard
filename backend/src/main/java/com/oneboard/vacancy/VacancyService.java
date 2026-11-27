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
        v.setTitle(truncate(f.title(), 500));
        v.setCompany(truncate(f.company(), 255));
        v.setLocation(truncate(f.location(), 255));
        v.setRemote(f.remote());
        v.setEmploymentType(truncate(f.employmentType(), 100));
        v.setSalary(truncate(f.salary(), 255));
        v.setDescription(truncate(f.description(), 100_000));
        v.setUrl(truncate(f.url(), 1000));
        v.setTags(f.tags() == null ? null : f.tags().stream().map(t -> truncate(t, 100)).distinct().toList());
        v.setPublishedAt(f.publishedAt());
        v.setFetchedAt(Instant.now());
        repository.save(v);
        return existing.isEmpty();
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
