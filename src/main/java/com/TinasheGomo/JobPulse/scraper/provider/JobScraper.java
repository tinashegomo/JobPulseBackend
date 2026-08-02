package com.TinasheGomo.JobPulse.scraper.provider;

import java.util.List;

public interface JobScraper {

    String getSource();

    List<ScrapedJob> scrape(String keywords, String location);
}
