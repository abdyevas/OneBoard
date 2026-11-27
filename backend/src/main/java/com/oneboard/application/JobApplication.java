package com.oneboard.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.*;

/**
 * The user's own record that they applied to a vacancy (the application itself happens on the
 * employer's / job board's site). One record per vacancy.
 */
@Entity
@Table(name = "job_application", uniqueConstraints = @UniqueConstraint(columnNames = "vacancy_id"))
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "vacancy_id", nullable = false)
    private Long vacancyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status;

    @Column(length = 2000)
    private String note;

    @Column(nullable = false)
    private Instant appliedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected JobApplication() {}

    public JobApplication(Long vacancyId, ApplicationStatus status, String note) {
        this.vacancyId = vacancyId;
        this.status = status;
        this.note = note;
        this.appliedAt = now();
        this.updatedAt = this.appliedAt;
    }

    public void update(ApplicationStatus status, String note) {
        this.status = status;
        this.note = note;
        this.updatedAt = now();
    }

    /** The DB keeps microseconds; truncate so in-memory and stored values are identical. */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public Long getId() { return id; }
    public Long getVacancyId() { return vacancyId; }
    public ApplicationStatus getStatus() { return status; }
    public String getNote() { return note; }
    public Instant getAppliedAt() { return appliedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
