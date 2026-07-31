package com.TinasheGomo.JobPulse.dto.resume;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResumeAnalysisRequest {
    @NotBlank(message = "Resume text is required")
    private String resumeText;
}
