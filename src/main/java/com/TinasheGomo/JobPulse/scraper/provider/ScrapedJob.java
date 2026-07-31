package com.TinasheGomo.JobPulse.scraper.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrapedJob {
    private String externalJobId;
    private String title;
    private String company;
    private String location;
    private String jobUrl;
    private String postedText;
    private LocalDateTime postedAt;
    @Builder.Default
    private List<String> tags = new ArrayList<>();
    private String description;
}
