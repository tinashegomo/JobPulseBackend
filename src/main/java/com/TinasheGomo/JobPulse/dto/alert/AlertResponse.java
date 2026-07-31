package com.TinasheGomo.JobPulse.dto.alert;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AlertResponse {
    private UUID id;
    private String keywords;
    private String workType;
    private String location;
    private String searchUrl;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}