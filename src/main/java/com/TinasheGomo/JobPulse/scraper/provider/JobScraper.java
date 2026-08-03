package com.TinasheGomo.JobPulse.scraper.provider;

import java.util.List;
import java.util.Set;

public interface JobScraper {

    String getSource();

    List<ScrapedJob> scrape(String keywords, String location);

    default Set<String> supportedLocations() {
        return Set.of();
    }
}
