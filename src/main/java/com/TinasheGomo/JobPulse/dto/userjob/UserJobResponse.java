package com.TinasheGomo.JobPulse.dto.userjob;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserJobResponse {
    private UUID id;
    private UUID jobId;
    private String source;
    private String externalJobId;
    private String title;
    private String company;
    private String location;
    private String url;
    private String description;
    private LocalDateTime postedAt;
    private Integer score;
    private Boolean seen;
    private Boolean hidden;
    private LocalDateTime notifiedAt;
    private LocalDateTime createdAt;
}