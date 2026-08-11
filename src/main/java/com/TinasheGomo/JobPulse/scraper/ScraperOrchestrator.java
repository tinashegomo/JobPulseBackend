package com.TinasheGomo.JobPulse.scraper;

import com.TinasheGomo.JobPulse.entity.Alert;
import com.TinasheGomo.JobPulse.entity.Job;
import com.TinasheGomo.JobPulse.repository.AlertRepository;
import com.TinasheGomo.JobPulse.scraper.provider.JobScraper;
import com.TinasheGomo.JobPulse.scraper.provider.ScrapedJob;
import com.TinasheGomo.JobPulse.service.JobService;
import com.TinasheGomo.JobPulse.service.UserJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScraperOrchestrator {

    private static final int MAX_JOB_AGE_HOURS = 10;
    private static final int SCORE_THRESHOLD = 50;

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "in", "on", "of", "for", "and", "or", "with", "is", "at", "to", "be");

    private final AlertRepository alertRepository;
    private final JobService jobService;
    private final UserJobService userJobService;
    private final List<JobScraper> scrapers;
    private final NotificationService notificationService;

    private final Map<String, CacheEntry> jobCache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, CacheEntry>> notifiedCache = new ConcurrentHashMap<>();

    private record CacheEntry(String status, long timestamp) {}

    public void run() {
        log.info("═══════════════════════════════════════════════════════");
        log.info("🚀 SCRAPER RUN — {} scrapers: LinkedIn, ApplyNow, Glassdoor", scrapers.size());
        log.info("   Pipeline: Scrape → Dedupe → Keyword Score → Save → Notify");
        log.info("═══════════════════════════════════════════════════════");

        long runStart = System.currentTimeMillis();
        jobCache.clear();
        notifiedCache.clear();

        List<Alert> alerts = new ArrayList<>(alertRepository.findAll().stream()
                .filter(a -> Boolean.TRUE.equals(a.getActive()))
                .filter(a -> a.getUser() != null)
                .toList());
        Collections.shuffle(alerts);
        log.info("Found {} active alert(s)", alerts.size());

        if (alerts.isEmpty()) {
            log.info("No active alerts — nothing to do");
            return;
        }

        int totalScraped = 0;
        int totalSaved = 0;

        for (JobScraper scraper : scrapers) {
            String source = scraper.getSource();
            long sourceStart = System.currentTimeMillis();

            log.info("───────────────────────────────────────────────────");
            log.info("📡 {}", source);

            try {
                Set<String> supportedLocations = scraper.supportedLocations();

                for (int i = 0; i < alerts.size(); i++) {
                    Alert alert = alerts.get(i);

                    if (!supportedLocations.isEmpty() && !matchesSupportedLocation(alert.getLocation(), supportedLocations)) {
                        continue;
                    }

                    if (i > 0) {
                        sleep(3000 + (long) (Math.random() * 5000));
                    }

                    log.info("[{}] ({}/{}) keyword='{}', location='{}'",
                            source, i + 1, alerts.size(), alert.getKeywords(), alert.getLocation());

                    // Phase 1: Scrape
                    List<ScrapedJob> rawJobs = scraper.scrape(alert.getKeywords(), alert.getLocation());
                    totalScraped += rawJobs.size();

                    // Phase 2: Dedupe + filter
                    List<ScrapedJob> filtered = dedupeAndFilter(rawJobs, alert, source);
                    log.info("[{}] {} scraped → {} after filter", source, rawJobs.size(), filtered.size());

                    // Phase 3: Score + save
                    List<String> alertKeywords = parseAlertKeywords(alert.getKeywords());
                    int saved = 0;

                    for (ScrapedJob job : filtered) {
                        if (userJobService.userHasJob(alert.getUser().getId(), job.getExternalJobId(), source)) {
                            continue;
                        }

                        int score = keywordScore(job, alertKeywords);
                        Job savedJob = saveNewJob(job, source);

                        userJobService.saveUserJob(alert.getUser(), savedJob, score);

                        if (score >= SCORE_THRESHOLD) {
                            log.info("[{}] ✅ {}/100: '{}' at '{}' — {} | posted: {}",
                                    source, score, job.getTitle(), job.getCompany(), job.getLocation(), job.getPostedAt());
                            try {
                                notificationService.notifyUser(
                                        alert.getUser().getId().toString(),
                                        "New Job: " + job.getTitle(),
                                        (job.getCompany() != null ? job.getCompany() : "Unknown") + " · " + (job.getLocation() != null ? job.getLocation() : ""),
                                        job.getJobUrl() != null ? job.getJobUrl() : ""
                                );
                            } catch (Exception e) {
                                log.error("[{}] Push notification failed: {}", source, e.getMessage());
                            }
                        } else {
                            log.debug("[{}] 📋 {}/100: '{}'", source, score, job.getTitle());
                        }
                        saved++;
                    }

                    totalSaved += saved;
                    log.info("[{}] {} jobs saved", source, saved);
                }

                long duration = (System.currentTimeMillis() - sourceStart) / 1000;
                log.info("📡 {} — done ({}s)", source, duration);

            } catch (Exception e) {
                log.error("📡 {} — FAILED: {}", source, e.getMessage());
            }
        }

        long runDuration = (System.currentTimeMillis() - runStart) / 1000;
        log.info("═══════════════════════════════════════════════════════");
        log.info("📊 DONE ({}s) — {} scraped, {} saved, {} users notified",
                runDuration, totalScraped, totalSaved, notifiedCache.size());
        log.info("═══════════════════════════════════════════════════════");
    }

    // ── Keyword scoring ──────────────────────────────────────────────────────

    private List<String> parseAlertKeywords(String keywords) {
        if (keywords == null || keywords.isBlank()) return List.of();
        return Arrays.stream(keywords.toLowerCase().split("\\s+"))
                .filter(w -> w.length() >= 2 && !STOPWORDS.contains(w))
                .toList();
    }

    private int keywordScore(ScrapedJob job, List<String> alertKeywords) {
        if (alertKeywords.isEmpty()) return 50;

        String titleLower = (job.getTitle() != null ? job.getTitle() : "").toLowerCase();
        String descLower = (job.getDescription() != null ? job.getDescription() : "").toLowerCase();
        String combined = titleLower + " " + descLower;

        int matched = 0;
        for (String keyword : alertKeywords) {
            if (combined.contains(keyword)) {
                matched++;
            }
        }

        return (int) Math.round((double) matched / alertKeywords.size() * 100);
    }

    // ── Dedupe + filter ──────────────────────────────────────────────────────

    private List<ScrapedJob> dedupeAndFilter(List<ScrapedJob> jobs, Alert alert, String source) {
        return jobs.stream()
                .filter(job -> isWithinAgeWindow(job))
                .filter(job -> matchesAlertKeyword(job, alert))
                .filter(job -> !isAlreadyRejected(job, source))
                .toList();
    }

    private boolean isWithinAgeWindow(ScrapedJob job) {
        if (job.getPostedAt() == null) {
            log.info("Rejected: no postedAt — '{}'", job.getTitle());
            return false;
        }
        long hours = Duration.between(job.getPostedAt(), LocalDateTime.now()).toHours();
        if (hours > MAX_JOB_AGE_HOURS) {
            log.info("Rejected: {}h old (max {}h) — '{}'", hours, MAX_JOB_AGE_HOURS, job.getTitle());
            return false;
        }
        return true;
    }

    private boolean isAlreadyRejected(ScrapedJob job, String source) {
        String jobKey = source + "_" + job.getExternalJobId();
        CacheEntry cached = jobCache.get(jobKey);
        return cached != null && "rejected".equals(cached.status());
    }

    private boolean matchesAlertKeyword(ScrapedJob job, Alert alert) {
        if (alert.getKeywords() == null || alert.getKeywords().isBlank()) return false;

        List<String> words = Arrays.stream(alert.getKeywords().toLowerCase().split("\\s+"))
                .filter(w -> w.length() >= 3 && !STOPWORDS.contains(w))
                .toList();
        if (words.isEmpty()) return false;

        String titleLower = (job.getTitle() != null ? job.getTitle() : "").toLowerCase();
        String descLower = (job.getDescription() != null ? job.getDescription() : "").toLowerCase();

        return words.stream().anyMatch(word -> titleLower.contains(word) || descLower.contains(word));
    }

    private boolean matchesSupportedLocation(String alertLocation, Set<String> supportedLocations) {
        if (alertLocation == null || alertLocation.isBlank()) return true;
        String locLower = alertLocation.toLowerCase().trim();
        return supportedLocations.stream()
                .anyMatch(s -> locLower.contains(s.toLowerCase()) || s.toLowerCase().contains(locLower));
    }

    // ── Save ─────────────────────────────────────────────────────────────────

    private Job saveNewJob(ScrapedJob job, String source) {
        Optional<Job> existing = jobService.getJobBySourceAndExternalId(source, job.getExternalJobId());
        if (existing.isPresent()) return existing.get();

        Job savedJob = new Job();
        savedJob.setSource(source);
        savedJob.setExternalJobId(job.getExternalJobId());
        savedJob.setTitle(job.getTitle());
        savedJob.setCompany(job.getCompany() != null ? job.getCompany() : "");
        savedJob.setLocation(job.getLocation() != null ? job.getLocation() : "");
        savedJob.setUrl(job.getJobUrl());
        savedJob.setDescription(job.getDescription() != null ? job.getDescription() : "");
        savedJob.setPostedAt(job.getPostedAt());
        jobService.saveJob(savedJob);
        log.info("[{}] 💾 '{}' at '{}'", source, job.getTitle(), job.getCompany());
        return savedJob;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
