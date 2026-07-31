package com.TinasheGomo.JobPulse.dto.keyword;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KeywordRequest {
    @NotBlank(message = "Keyword is required")
    private String keyword;
}
