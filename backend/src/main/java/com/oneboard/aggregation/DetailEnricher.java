package com.oneboard.aggregation;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.oneboard.vacancy.Vacancy;
import com.oneboard.vacancy.VacancyService;
import com.oneboard.vacancy.source.PoliteHttp;
import com.oneboard.vacancy.source.VacancySource;

/**
 * Background worker that loads full details for vacancies stored as summaries, one request at a time
 * per site and never faster than the site's crawl delay. Vacancies the user opens go first.
 */
@Component
public class DetailEnricher {

    private static final Logger log = LoggerFactory.getLogger(DetailEnricher.class);

    private final List<VacancySource> sources;
    private final VacancyService vacancies;
    private final PoliteHttp polite;
    /** Vacancy ids the user is looking at right now. */
    private final Set<Long> priority = ConcurrentHashMap.newKeySet();
    /** Vacancies that failed to load, so we don't retry them in a tight loop. */
    private final Set<Long> failed = ConcurrentHashMap.newKeySet();

    public DetailEnricher(List<VacancySource> sources, VacancyService vacancies, PoliteHttp polite) {
        this.sources = sources;
        this.vacancies = vacancies;
        this.polite = polite;
    }

    /** Ask for this vacancy's details to be loaded next (if it still lacks them). */
    public void prioritize(Vacancy v) {
        if (v.getDescription() == null && sourceOf(v).flatMap(s -> Optional.ofNullable(s.detailsHost())).isPresent()) {
            failed.remove(v.getId());
            priority.add(v.getId());
        }
    }

    @Scheduled(fixedDelay = 2000, initialDelay = 5000)
    void tick() {
        for (VacancySource source : sources) {
            if (source.detailsHost() == null || !source.enabled()) continue;
            Optional<Vacancy> next = nextFor(source);
            if (next.isEmpty()) continue;
            // non-blocking: if the site's crawl delay hasn't passed yet, try again on a later tick
            if (!polite.tryAcquire(source.detailsHost(), source.detailsDelay())) continue;
            Vacancy v = next.get();
            priority.remove(v.getId());
            try {
                source.fetchDetails(v).ifPresentOrElse(
                        f -> vacancies.completeDetails(v.getId(), f),
                        () -> failed.add(v.getId()));
            } catch (RuntimeException e) {
                failed.add(v.getId());
                log.warn("Details for {} #{} failed: {}", source.id(), v.getId(), e.toString());
            }
        }
    }

    private Optional<Vacancy> nextFor(VacancySource source) {
        for (Long id : priority) {
            try {
                Vacancy v = vacancies.get(id);
                if (v.getSource().equals(source.id()) && v.getDescription() == null) return Optional.of(v);
                if (v.getDescription() != null) priority.remove(id);
            } catch (RuntimeException gone) {
                priority.remove(id);
            }
        }
        return vacancies.nextWithoutDetails(source.id(), Set.copyOf(failed));
    }

    private Optional<VacancySource> sourceOf(Vacancy v) {
        return sources.stream().filter(s -> s.id().equals(v.getSource())).findFirst();
    }
}
