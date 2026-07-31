package com.TinasheGomo.JobPulse.dto.resumeprofile;

import jakarta.validation.constraints.NotBlank;
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
}