package com.TinasheGomo.JobPulse.dto.apikey;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiKeyResponse {
    private UUID id;
    private String provider;
    private String maskedKey;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}