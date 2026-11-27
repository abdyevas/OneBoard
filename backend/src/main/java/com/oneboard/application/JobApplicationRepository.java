package com.oneboard.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    Optional<JobApplication> findByVacancyId(Long vacancyId);

    List<JobApplication> findByVacancyIdIn(Collection<Long> vacancyIds);

    List<JobApplication> findAllByOrderByUpdatedAtDesc();
}
