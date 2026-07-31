package com.TinasheGomo.JobPulse.dto.resume;

import lombok.*;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResumeAnalysisResponse {
    private String name;
    private String title;
    private Integer yearsExperience;
    private String level;
    private List<String> skills;
    private List<String> tools;
    private List<String> languages;
    private List<String> frameworks;
    private List<String> cloudSkills;
    private List<String> preferredRoles;
    private List<String> avoidRoles;
    private String education;
    private String location;
    private String workPreference;
    private List<String> highlights;
}
