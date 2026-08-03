package com.TinasheGomo.JobPulse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "job_profiles")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class JobProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false, unique = true)
    private Job job;

    @Column(nullable = false)
    private String source;

    @Column(name = "external_job_id", nullable = false)
    private String externalJobId;

    private String title;

    @Column(name = "level")
    private String level;

    @Column(name = "work_type")
    private String workType;

    @Column(name = "role_category")
    private String roleCategory;

    @Column(name = "required_skills", columnDefinition = "TEXT")
    private String requiredSkills;

    @Column(name = "bonus_skills", columnDefinition = "TEXT")
    private String bonusSkills;

    @Column(name = "location_normalized")
    private String locationNormalized;

    @Column(name = "is_recruiting_agency")
    private boolean recruitingAgency;

    private double confidence;

    @Column(name = "extracted_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime extractedAt;

    @PrePersist
    public void onCreate() {
        this.extractedAt = LocalDateTime.now();
    }
}
