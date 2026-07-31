package com.TinasheGomo.JobPulse.dto.resumeprofile;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResumeProfileResponse {
    private UUID id;
    private String skills;
    private String preferredRoles;
    private String level;
    private String workPreference;
    private String resumeText;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}