package com.oneboard.adaptation;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.*;

/**
 * A CV tailored for one vacancy. Stores snapshots of titles so history survives
 * deletion of the source CV / vacancy.
 */
@Entity
@Table(name = "cv_adaptation", indexes = {@Index(columnList = "cv_id"), @Index(columnList = "vacancy_id")})
public class CvAdaptation {

    private static final String SEP = "\n";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cv_id", nullable = false)
    private Long cvId;

    @Column(name = "vacancy_id", nullable = false)
    private Long vacancyId;

    @Column(length = 255)
    private String cvTitle;

    @Column(length = 500)
    private String vacancyTitle;

    @Column(length = 255)
    private String company;

    @Column(nullable = false, length = 40)
    private String method;

    @Column(length = 100)
    private String model;

    private int matchScore;

    @Column(length = 4000)
    private String matchedSkills;

    @Column(length = 4000)
    private String missingSkills;

    @Column(length = 20_000)
    private String notes;

    @Column(nullable = false, length = 100_000)
    private String content;

    @Column(nullable = false)
    private Instant createdAt;

    protected CvAdaptation() {}

    public CvAdaptation(Long cvId, String cvTitle, Long vacancyId, String vacancyTitle, String company,
                        String method, String model, int matchScore, List<String> matched, List<String> missing,
                        List<String> notes, String content) {
        this.cvId = cvId;
        this.cvTitle = cvTitle;
        this.vacancyId = vacancyId;
        this.vacancyTitle = vacancyTitle;
        this.company = company;
        this.method = method;
        this.model = model;
        this.matchScore = matchScore;
        this.matchedSkills = String.join(SEP, matched);
        this.missingSkills = String.join(SEP, missing);
        this.notes = String.join(SEP, notes);
        this.content = content;
        this.createdAt = Instant.now();
    }

    private static List<String> split(String s) {
        return s == null || s.isEmpty() ? List.of() : Arrays.asList(s.split(SEP));
    }

    public Long getId() { return id; }
    public Long getCvId() { return cvId; }
    public Long getVacancyId() { return vacancyId; }
    public String getCvTitle() { return cvTitle; }
    public String getVacancyTitle() { return vacancyTitle; }
    public String getCompany() { return company; }
    public String getMethod() { return method; }
    public String getModel() { return model; }
    public int getMatchScore() { return matchScore; }
    public List<String> getMatchedSkills() { return split(matchedSkills); }
    public List<String> getMissingSkills() { return split(missingSkills); }
    public List<String> getNotes() { return split(notes); }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
