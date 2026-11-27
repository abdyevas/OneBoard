package com.oneboard.vacancy;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VacancyRepository extends JpaRepository<Vacancy, Long> {

    Optional<Vacancy> findBySourceAndExternalId(String source, String externalId);

    List<Vacancy> findBySourceNotIn(java.util.Collection<String> sources);

    @Query("""
            select v from Vacancy v
            where (:q is null
                   or lower(v.title) like :q
                   or lower(v.company) like :q
                   or lower(v.description) like :q)
              and (:source is null or v.source = :source)
              and (:remote is null or v.remote = :remote)
            """)
    Page<Vacancy> search(@Param("q") String likePattern,
                         @Param("source") String source,
                         @Param("remote") Boolean remote,
                         Pageable pageable);

    @Query("select v.source, count(v) from Vacancy v group by v.source")
    List<Object[]> countBySource();
}
