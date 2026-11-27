package com.oneboard.vacancy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.*;

@Entity
@Table(name = "vacancy",
        uniqueConstraints = @UniqueConstraint(columnNames = {"source", "external_id"}),
        indexes = {@Index(columnList = "published_at"), @Index(columnList = "source")})
public class Vacancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Source identifier, e.g. "jooble", "manual". */
    @Column(nullable = false, length = 40)
    private String source;

    @Column(name = "external_id", nullable = false, length = 255)
    private String externalId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 255)
    private String company;

    @Column(length = 255)
    private String location;

    private boolean remote;

    @Column(length = 100)
    private String employmentType;

    @Column(length = 255)
    private String salary;

    @Column(length = 100_000)
    private String description;

    @Column(length = 1000)
    private String url;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "vacancy_tag", joinColumns = @JoinColumn(name = "vacancy_id"))
    @Column(name = "tag", length = 100)
    private List<String> tags = new ArrayList<>();

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private Instant fetchedAt;

    /** Application deadline, when the board publishes one. */
    private Instant deadlineAt;

    protected Vacancy() {}

    public Vacancy(String source, String externalId) {
        this.source = source;
        this.externalId = externalId;
    }

    public Long getId() { return id; }
    public String getSource() { return source; }
    public String getExternalId() { return externalId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public boolean isRemote() { return remote; }
    public void setRemote(boolean remote) { this.remote = remote; }
    public String getEmploymentType() { return employmentType; }
    public void setEmploymentType(String employmentType) { this.employmentType = employmentType; }
    public String getSalary() { return salary; }
    public void setSalary(String salary) { this.salary = salary; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) {
        this.tags.clear();
        if (tags != null) this.tags.addAll(tags);
    }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }
    public Instant getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(Instant deadlineAt) { this.deadlineAt = deadlineAt; }
}
