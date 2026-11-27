package com.oneboard.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.oneboard.common.NotFoundException;
import com.oneboard.vacancy.VacancyRepository;

@Service
public class JobApplicationService {

    private final JobApplicationRepository repository;
    private final VacancyRepository vacancies;

    public JobApplicationService(JobApplicationRepository repository, VacancyRepository vacancies) {
        this.repository = repository;
        this.vacancies = vacancies;
    }

    @Transactional(readOnly = true)
    public Optional<JobApplication> forVacancy(Long vacancyId) {
        return repository.findByVacancyId(vacancyId);
    }

    @Transactional(readOnly = true)
    public Map<Long, JobApplication> forVacancies(Collection<Long> vacancyIds) {
        if (vacancyIds.isEmpty()) return Map.of();
        return repository.findByVacancyIdIn(vacancyIds).stream()
                .collect(Collectors.toMap(JobApplication::getVacancyId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public List<JobApplication> list() {
        return repository.findAllByOrderByUpdatedAtDesc();
    }

    /** Create or update the application record for a vacancy. */
    @Transactional
    public JobApplication set(Long vacancyId, ApplicationStatus status, String note) {
        if (!vacancies.existsById(vacancyId)) throw new NotFoundException("Vacancy", vacancyId);
        return repository.findByVacancyId(vacancyId)
                .map(a -> { a.update(status, note); return a; })
                .orElseGet(() -> repository.save(new JobApplication(vacancyId, status, note)));
    }

    @Transactional
    public void remove(Long vacancyId) {
        repository.findByVacancyId(vacancyId).ifPresent(repository::delete);
    }
}
