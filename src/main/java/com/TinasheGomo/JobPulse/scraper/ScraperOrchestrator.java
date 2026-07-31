package com.TinasheGomo.JobPulse.scraper;

import com.TinasheGomo.JobPulse.entity.Alert;
import com.TinasheGomo.JobPulse.entity.Job;
import com.TinasheGomo.JobPulse.dto.resumeprofile.ResumeProfileResponse;
import com.TinasheGomo.JobPulse.repository.AlertRepository;
import com.TinasheGomo.JobPulse.scraper.ai.JobExtractor;
import com.TinasheGomo.JobPulse.scraper.ai.SemanticChecker;
import com.TinasheGomo.JobPulse.scraper.provider.ApplyNowScraper;
import com.TinasheGomo.JobPulse.scraper.provider.LinkedInScraper;
import com.TinasheGomo.JobPulse.scraper.provider.RemoteOkScraper;
import com.TinasheGomo.JobPulse.scraper.provider.ScrapedJob;
import com.TinasheGomo.JobPulse.service.JobService;
import com.TinasheGomo.JobPulse.service.ResumeProfileService;
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
    private static final int AI_SCORE_THRESHOLD = 50;

    private static final Set<String> STOPWORDS = Set.of("a", "an", "the", "in", "on", "of", "for", "and", "or");

    private final AlertRepository alertRepository;
    private final JobService jobService;
    private final UserJobService userJobService;
    private final ResumeProfileService resumeProfileService;
    private final LinkedInScraper linkedInScraper;
    private final RemoteOkScraper remoteOkScraper;
    private final ApplyNowScraper applyNowScraper;
    private final JobExtractor jobExtractor;
    private final SemanticChecker semanticChecker;
    private final NotificationService notificationService;

    private final Map<String, CacheEntry> jobCache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, CacheEntry>> notifiedCache = new ConcurrentHashMap<>();

    private record CacheEntry(String status, long timestamp) {}

    public void run() {
        log.info("Fetching active alerts from database...");
        jobCache.clear();
        notifiedCache.clear();

        List<Alert> alerts = new ArrayList<>(alertRepository.findAll().stream()
                .filter(a -> Boolean.TRUE.equals(a.getActive()))
                .filter(a -> a.getUser() != null)
                .toList());
        Collections.shuffle(alerts);
        log.info("Found {} active alert(s) to process", alerts.size());

        if (alerts.isEmpty()) {
            log.info("No active alerts — nothing to do");
            return;
        }

        // LinkedIn
        long linkedInStart = System.currentTimeMillis();
        log.info("───────────────────────────────────────────────────");
        log.info("📡 LINKEDIN — scraping {} alerts", alerts.size());
        processLinkedIn(alerts);
        long linkedInDuration = (System.currentTimeMillis() - linkedInStart) / 1000;
        log.info("📡 LINKEDIN — done ({}s)", linkedInDuration);

        // RemoteOK
        long remoteOkStart = System.currentTimeMillis();
        log.info("───────────────────────────────────────────────────");
        log.info("📡 REMOTEOK — fetching global feed");
        processRemoteOk(alerts);
        long remoteOkDuration = (System.currentTimeMillis() - remoteOkStart) / 1000;
        log.info("📡 REMOTEOK — done ({}s)", remoteOkDuration);

        // ApplyNow
        long applyNowStart = System.currentTimeMillis();
        log.info("───────────────────────────────────────────────────");
        log.info("📡 APPLYNOW — fetching Zimbabwe listings");
        processApplyNow(alerts);
        long applyNowDuration = (System.currentTimeMillis() - applyNowStart) / 1000;
        log.info("📡 APPLYNOW — done ({}s)", applyNowDuration);

        log.info("───────────────────────────────────────────────────");
        log.info("📊 SUMMARY: {} jobs in cache, {} users notified",
                jobCache.size(), notifiedCache.size());
    }

    private void processLinkedIn(List<Alert> alerts) {
        int totalFound = 0;
        int totalRecent = 0;
        int totalSaved = 0;

        for (int i = 0; i < alerts.size(); i++) {
            Alert alert = alerts.get(i);
            try {
                if (i > 0) {
                    long delay = 3000 + (long) (Math.random() * 5000);
                    log.info("[LinkedIn] Rate-limit delay {}ms before next alert", delay);
                    sleep(delay);
                }

                log.info("[LinkedIn] ({}/{}) Scraping for keyword='{}', location='{}'",
                        i + 1, alerts.size(), alert.getKeywords(), alert.getLocation());

                String searchUrl = alert.getSearchUrl();
                if (searchUrl == null || searchUrl.isBlank()) {
                    searchUrl = buildLinkedInSearchUrl(alert);
                }
                log.info("[LinkedIn] URL: {}", searchUrl);

                List<ScrapedJob> rawJobs = linkedInScraper.scrape(searchUrl);
                totalFound += rawJobs.size();
                log.info("[LinkedIn] Found {} raw jobs for '{}'", rawJobs.size(), alert.getKeywords());

                List<ScrapedJob> recentJobs = rawJobs.stream()
                        .filter(job -> {
                            if (job.getPostedAt() == null) return true;
                            long hours = Duration.between(job.getPostedAt(), LocalDateTime.now()).toHours();
                            return hours <= MAX_JOB_AGE_HOURS;
                        })
                        .toList();
                totalRecent += recentJobs.size();

                log.info("[LinkedIn] {} of {} jobs within {}h window",
                        recentJobs.size(), rawJobs.size(), MAX_JOB_AGE_HOURS);

                for (ScrapedJob job : recentJobs) {
                    boolean saved = processJobForAlert(job, "LINKEDIN", alert);
                    if (saved) totalSaved++;
                }
            } catch (Exception e) {
                log.error("[LinkedIn] Error processing alert '{}': {}", alert.getKeywords(), e.getMessage());
            }
        }

        log.info("[LinkedIn] TOTAL: {} found, {} recent, {} saved", totalFound, totalRecent, totalSaved);
    }

    private void processRemoteOk(List<Alert> alerts) {
        try {
            List<ScrapedJob> remoteOkJobs = remoteOkScraper.scrape();
            log.info("[RemoteOK] Fetched {} total listings from API", remoteOkJobs.size());

            int totalMatches = 0;
            int totalSaved = 0;

            for (Alert alert : alerts) {
                List<ScrapedJob> matches = remoteOkJobs.stream()
                        .filter(job -> matchesAlertKeyword(job, alert))
                        .filter(job -> {
                            if (job.getPostedAt() == null) return true;
                            long hours = Duration.between(job.getPostedAt(), LocalDateTime.now()).toHours();
                            return hours <= MAX_JOB_AGE_HOURS;
                        })
                        .toList();
                totalMatches += matches.size();

                log.info("[RemoteOK] Alert '{}' — {} matches within {}h",
                        alert.getKeywords(), matches.size(), MAX_JOB_AGE_HOURS);

                for (ScrapedJob job : matches) {
                    boolean saved = processJobForAlert(job, "REMOTEOK", alert);
                    if (saved) totalSaved++;
                }
            }

            log.info("[RemoteOK] TOTAL: {} matches across {} alerts, {} saved", totalMatches, alerts.size(), totalSaved);
        } catch (Exception e) {
            log.error("[RemoteOK] Error processing feed: {}", e.getMessage());
        }
    }

    private void processApplyNow(List<Alert> alerts) {
        try {
            List<ScrapedJob> applyNowJobs = applyNowScraper.scrape();
            log.info("[ApplyNow] Fetched {} total listings from site", applyNowJobs.size());

            List<Alert> zimbabweAlerts = alerts.stream()
                    .filter(a -> a.getLocation() != null && a.getLocation().toLowerCase().contains("zimbabwe"))
                    .toList();
            log.info("[ApplyNow] {} alerts match Zimbabwe location filter", zimbabweAlerts.size());

            int totalMatches = 0;
            int totalSaved = 0;

            for (Alert alert : zimbabweAlerts) {
                List<ScrapedJob> matches = applyNowJobs.stream()
                        .filter(job -> matchesAlertKeyword(job, alert))
                        .filter(job -> {
                            if (job.getPostedAt() == null) return true;
                            long hours = Duration.between(job.getPostedAt(), LocalDateTime.now()).toHours();
                            return hours <= MAX_JOB_AGE_HOURS;
                        })
                        .toList();
                totalMatches += matches.size();

                log.info("[ApplyNow] Alert '{}' — {} matches within {}h",
                        alert.getKeywords(), matches.size(), MAX_JOB_AGE_HOURS);

                for (ScrapedJob job : matches) {
                    boolean saved = processJobForAlert(job, "APPLYNOW", alert);
                    if (saved) totalSaved++;
                }
            }

            log.info("[ApplyNow] TOTAL: {} matches across {} alerts, {} saved", totalMatches, zimbabweAlerts.size(), totalSaved);
        } catch (Exception e) {
            log.error("[ApplyNow] Error processing feed: {}", e.getMessage());
        }
    }

    private boolean processJobForAlert(ScrapedJob job, String source, Alert alert) {
        String jobKey = source + "_" + job.getExternalJobId();
        String userKey = alert.getUser().getId().toString();

        CacheEntry cached = jobCache.get(jobKey);
        if (cached != null && "rejected".equals(cached.status())) {
            log.debug("[{}] SKIP (already rejected): {} at {}", source, job.getTitle(), job.getCompany());
            return false;
        }

        // AI scoring for non-LinkedIn sources
        if (cached == null && !"LINKEDIN".equals(source)) {
            log.info("[{}] Scoring job: '{}' at '{}' for user {}...",
                    source, job.getTitle(), job.getCompany(), alert.getUser().getId());

            var scoreResult = scoreJobFull(job, alert);
            if (scoreResult != null && scoreResult.score() < AI_SCORE_THRESHOLD) {
                jobCache.put(jobKey, new CacheEntry("rejected", System.currentTimeMillis()));
                log.info("[{}] ❌ REJECTED (score {}/100): {} at {} — {}",
                        source, scoreResult.score(), job.getTitle(), job.getCompany(), scoreResult.reason());
                return false;
            }
            if (scoreResult != null) {
                log.info("[{}] ✅ ACCEPTED (score {}/100): {} — {}",
                        source, scoreResult.score(), job.getTitle(), scoreResult.reason());
            }
        }

        Optional<Job> existingJob = jobService.getJobBySourceAndExternalId(source, job.getExternalJobId());
        if (existingJob.isPresent()) {
            log.debug("[{}] SKIP (already exists): {}", source, jobKey);
            return false;
        }

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
        log.info("[{}] 💾 SAVED job: '{}' at '{}' (id={})", source, job.getTitle(), job.getCompany(), savedJob.getId());

        jobCache.put(jobKey, new CacheEntry("accepted", System.currentTimeMillis()));

        Map<String, CacheEntry> userNotified = notifiedCache.computeIfAbsent(userKey, k -> new ConcurrentHashMap<>());
        if (!userNotified.containsKey(jobKey)) {
            Integer score = null;
            if (!"LINKEDIN".equals(source)) {
                var scoreResult = scoreJobFull(job, alert);
                if (scoreResult != null) {
                    score = scoreResult.score();
                }
            }

            userJobService.saveUserJob(alert.getUser(), savedJob, score);
            log.info("[{}] 🔔 NOTIFIED user {} for '{}'", source, alert.getUser().getId(), job.getTitle());

            try {
                notificationService.notifyUser(
                        alert.getUser().getId().toString(),
                        "New Job: " + job.getTitle(),
                        (job.getCompany() != null ? job.getCompany() : "Unknown") + " · " + (job.getLocation() != null ? job.getLocation() : ""),
                        job.getJobUrl() != null ? job.getJobUrl() : ""
                );
            } catch (Exception e) {
                log.error("[{}] Failed to send push notification to user {}: {}", source, alert.getUser().getId(), e.getMessage());
            }

            userNotified.put(jobKey, new CacheEntry("notified", System.currentTimeMillis()));
            return true;
        }
        return false;
    }

    private record ScoreResult(int score, String reason, List<String> matchedSkills, List<String> missingSkills) {}

    private ScoreResult scoreJobFull(ScrapedJob job, Alert alert) {
        try {
            log.info("  [AI] Extracting job profile for '{}' at '{}'...", job.getTitle(), job.getCompany());
            var jobProfile = jobExtractor.extract(job);
            if (jobProfile == null) {
                log.warn("  [AI] JobExtractor returned null for '{}' — skipping scoring", job.getTitle());
                return null;
            }
            log.info("  [AI] Extracted: level={}, workType={}, role={}, requiredSkills={}",
                    jobProfile.level(), jobProfile.workType(), jobProfile.roleCategory(), jobProfile.requiredSkills());

            ResumeProfileResponse resumeProfile = resumeProfileService.getProfileByUser(alert.getUser());
            if (resumeProfile == null || resumeProfile.getResumeText() == null || resumeProfile.getResumeText().isBlank()) {
                log.info("  [AI] No resume for user {} — defaulting to score 50", alert.getUser().getId());
                return new ScoreResult(50, "no resume uploaded", List.of(), List.of());
            }

            RuleEngine.CandidateProfile candidate = RuleEngine.CandidateProfile.builder()
                    .level(resumeProfile.getLevel() != null ? resumeProfile.getLevel() : "mid")
                    .workPreference(resumeProfile.getWorkPreference() != null ? resumeProfile.getWorkPreference() : "any")
                    .preferredRoles(resumeProfile.getPreferredRoles() != null
                            ? Arrays.asList(resumeProfile.getPreferredRoles().split(",\\s*"))
                            : List.of())
                    .skills(resumeProfile.getSkills() != null
                            ? Arrays.asList(resumeProfile.getSkills().split(",\\s*"))
                            : List.of())
                    .tools(List.of())
                    .cloudSkills(List.of())
                    .location(null)
                    .build();

            log.info("  [AI] Running semantic check for '{}' vs candidate...", job.getTitle());
            int semanticScore = 0;
            try {
                var semanticResult = semanticChecker.check(candidate, job);
                if (semanticResult != null) {
                    semanticScore = semanticResult.score();
                    log.info("  [AI] Semantic score: {}/5 — {}", semanticScore, semanticResult.reason());
                }
            } catch (Exception e) {
                log.warn("  [AI] Semantic check failed: {}", e.getMessage());
            }

            RuleEngine.JobProfile jobProfileForEngine = RuleEngine.JobProfile.builder()
                    .title(jobProfile.title())
                    .level(jobProfile.level())
                    .workType(jobProfile.workType())
                    .requiredSkills(jobProfile.requiredSkills())
                    .bonusSkills(jobProfile.bonusSkills())
                    .roleCategory(jobProfile.roleCategory())
                    .locationNormalized(jobProfile.locationNormalized())
                    .location(job.getLocation())
                    .isRecruitingAgency(jobProfile.isRecruitingAgency())
                    .build();

            RuleEngine.AlertData alertData = RuleEngine.AlertData.builder()
                    .keyword(alert.getKeywords())
                    .workType(alert.getWorkType())
                    .location(alert.getLocation())
                    .build();

            RuleEngine.ScoreResult result = RuleEngine.score(candidate, jobProfileForEngine, alertData, semanticScore);
            log.info("  [RuleEngine] FINAL SCORE: {}/100 — {}", result.getScore(), result.getReason());
            log.info("  [RuleEngine] Breakdown: {}", result.getBreakdown());
            if (!result.getMatchedSkills().isEmpty()) {
                log.info("  [RuleEngine] Matched skills: {}", result.getMatchedSkills());
            }
            if (!result.getMissingSkills().isEmpty()) {
                log.info("  [RuleEngine] Missing skills: {}", result.getMissingSkills());
            }

            return new ScoreResult(result.getScore(), result.getReason(), result.getMatchedSkills(), result.getMissingSkills());
        } catch (Exception e) {
            log.error("  [AI] Job scoring failed for '{}' at '{}': {}", job.getTitle(), job.getCompany(), e.getMessage());
            return null;
        }
    }

    private boolean matchesAlertKeyword(ScrapedJob job, Alert alert) {
        if (alert.getKeywords() == null || alert.getKeywords().isBlank()) {
            return false;
        }

        List<String> words = Arrays.stream(alert.getKeywords().toLowerCase().split("\\s+"))
                .filter(w -> w.length() >= 3 && !STOPWORDS.contains(w))
                .toList();

        if (words.isEmpty()) return false;

        String titleLower = (job.getTitle() != null ? job.getTitle() : "").toLowerCase();
        String descLower = (job.getDescription() != null ? job.getDescription() : "").toLowerCase();
        List<String> tagsLower = job.getTags() != null
                ? job.getTags().stream().map(String::toLowerCase).toList()
                : List.of();

        return words.stream().anyMatch(word ->
                titleLower.contains(word) || descLower.contains(word) || tagsLower.stream().anyMatch(tag -> tag.contains(word)));
    }

    private String buildLinkedInSearchUrl(Alert alert) {
        StringBuilder url = new StringBuilder("https://www.linkedin.com/jobs/search/?");

        if (alert.getKeywords() != null && !alert.getKeywords().isBlank()) {
            url.append("keywords=").append(alert.getKeywords().replace(" ", "%20"));
        }
        if (alert.getLocation() != null && !alert.getLocation().isBlank()) {
            url.append("&location=").append(alert.getLocation().replace(" ", "%20"));
        }

        return url.toString();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
