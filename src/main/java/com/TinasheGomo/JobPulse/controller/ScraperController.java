package com.TinasheGomo.JobPulse.controller;

import com.TinasheGomo.JobPulse.scraper.ScraperJob;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/scraper")
@RequiredArgsConstructor
public class ScraperController {

    private final ScraperJob scraperJob;

    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> run() {
        scraperJob.executeAsync();
        return ResponseEntity.ok(Map.of(
                "status", "started",
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
