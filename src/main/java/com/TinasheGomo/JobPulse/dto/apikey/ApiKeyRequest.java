package com.TinasheGomo.JobPulse.dto.apikey;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiKeyRequest {
    @NotBlank
    private String provider;

    @NotBlank
    private String apiKey;
}