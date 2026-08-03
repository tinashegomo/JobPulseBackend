package com.TinasheGomo.JobPulse.controller;

import com.TinasheGomo.JobPulse.scraper.ScraperJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/scraper")
@RequiredArgsConstructor
@Slf4j
public class ScraperController {

    private final ScraperJob scraperJob;

    @PostMapping("/run")
    public ResponseEntity<Map<String, String>> run() {
        try {
            scraperJob.executeAsync();
        } catch (Exception e) {
            log.error("[ScraperController] Failed to start scraper: {}", e.getMessage());
        }
        return ResponseEntity.ok(Map.of("status", "started"));
    }
}
