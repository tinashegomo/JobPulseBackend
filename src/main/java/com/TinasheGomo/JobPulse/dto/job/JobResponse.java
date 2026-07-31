package com.TinasheGomo.JobPulse.dto.job;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class JobResponse {
    private UUID id;
    private String source;
    private String externalJobId;
    private String title;
    private String company;
    private String location;
    private String url;
    private String description;
    private LocalDateTime postedAt;
    private LocalDateTime createdAt;
}