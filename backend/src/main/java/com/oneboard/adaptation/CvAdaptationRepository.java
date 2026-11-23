package com.oneboard.adaptation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CvAdaptationRepository extends JpaRepository<CvAdaptation, Long> {

    @Query("""
            select a from CvAdaptation a
            where (:cvId is null or a.cvId = :cvId)
              and (:vacancyId is null or a.vacancyId = :vacancyId)
            order by a.createdAt desc
            """)
    List<CvAdaptation> find(@Param("cvId") Long cvId, @Param("vacancyId") Long vacancyId);
}
