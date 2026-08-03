package com.TinasheGomo.JobPulse.dto.jobprofile;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JobProfileResponse {
    private UUID id;
    private UUID jobId;
    private String source;
    private String externalJobId;
    private String title;
    private String level;
    private String workType;
    private String roleCategory;
    private List<String> requiredSkills;
    private List<String> bonusSkills;
    private String locationNormalized;
    private boolean recruitingAgency;
    private double confidence;
    private LocalDateTime extractedAt;
}
