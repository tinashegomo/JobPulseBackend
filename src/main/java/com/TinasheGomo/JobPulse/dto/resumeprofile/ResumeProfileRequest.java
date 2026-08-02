package com.TinasheGomo.JobPulse.dto.resumeprofile;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResumeProfileRequest {
    private String skills;
    private String preferredRoles;
    private String level;
    private String workPreference;
    private String resumeText;
    private String profile;
    private String originalFileName;
}