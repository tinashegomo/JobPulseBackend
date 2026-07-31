package com.TinasheGomo.JobPulse.dto.keyword;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KeywordResponse {
    private UUID id;
    private String keyword;
    private LocalDateTime createdAt;
}
