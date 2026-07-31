package com.TinasheGomo.JobPulse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_jobs")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class UserJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Setter(AccessLevel.NONE)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    private Integer score;

    @Builder.Default
    private Boolean seen = false;

    @Builder.Default
    private Boolean hidden = false;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}