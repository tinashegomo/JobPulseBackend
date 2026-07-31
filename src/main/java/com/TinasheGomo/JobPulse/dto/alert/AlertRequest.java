package com.TinasheGomo.JobPulse.dto.alert;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AlertRequest {
    @NotBlank
    private String keywords;

    @NotBlank
    private String workType;

    private String location;

    private String searchUrl;

    private Boolean active;
}