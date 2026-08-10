package com.TinasheGomo.JobPulse.scraper;

import com.TinasheGomo.JobPulse.entity.Alert;
import com.TinasheGomo.JobPulse.entity.Job;
import com.TinasheGomo.JobPulse.dto.resumeprofile.ResumeProfileResponse;
import com.TinasheGomo.JobPulse.repository.AlertRepository;
import com.TinasheGomo.JobPulse.scraper.provider.JobScraper;
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

    private static final int MAX_JOB_AGE_HOURS = 15;
    private static final int SCORE_THRESHOLD = 50;

    private static final Set<String> STOPWORDS = Set.of("a", "an", "the", "in", "on", "of", "for", "and", "or");

    private final AlertRepository alertRepository;
    private final JobService jobService;
    private final UserJobService userJobService;
    private final ResumeProfileService resumeProfileService;
    private final List<JobScraper> scrapers;
    private final NotificationService notificationService;

    private final Map<String, CacheEntry> jobCache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, CacheEntry>> notifiedCache = new ConcurrentHashMap<>();

    private record CacheEntry(String status, long timestamp) {}

    public void run() {
        log.info("═══════════════════════════════════════════════════════");
        log.info("🚀 DETERMINISTIC SCRAPER RUN — {} scrapers registered", scrapers.size());
        log.info("   Pipeline: Scrape → Dedupe → Extract (no AI) → Score (deterministic) → Notify");
        log.info("═══════════════════════════════════════════════════════");

        long runStart = System.currentTimeMillis();
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

        // Phase 0: Pre-load resume profiles (skills per user) — once per run
        Map<UUID, ResumeProfileResponse> resumeCache = new HashMap<>();
        log.info("Phase 0: Pre-loading resume profiles...");
        for (Alert alert : alerts) {
            UUID userId = alert.getUser().getId();
            if (!resumeCache.containsKey(userId)) {
                ResumeProfileResponse profile = resumeProfileService.getProfileByUser(alert.getUser());
                resumeCache.put(userId, profile);
            }
        }
        log.info("Phase 0: Loaded {} resume profiles", resumeCache.size());

        int totalSourcesProcessed = 0;
        int totalSourcesFailed = 0;
        int totalJobsScraped = 0;
        int totalJobsAfterFilter = 0;
        int totalExtracted = 0;
        int totalSaved = 0;

        for (JobScraper scraper : scrapers) {
            String source = scraper.getSource();
            long sourceStart = System.currentTimeMillis();

            log.info("───────────────────────────────────────────────────");
            log.info("📡 {} — scraping {} alerts", source, alerts.size());

            try {
                SourceResult result = processScraper(scraper, alerts, resumeCache);
                long duration = (System.currentTimeMillis() - sourceStart) / 1000;
                log.info("📡 {} — done ({}s): {} scraped → {} after filter → {} extracted → {} saved",
                        source, duration,
                        result.scraped, result.afterFilter, result.extracted, result.saved);
                totalSourcesProcessed++;
                totalJobsScraped += result.scraped;
                totalJobsAfterFilter += result.afterFilter;
                totalExtracted += result.extracted;
                totalSaved += result.saved;
            } catch (Exception e) {
                long duration = (System.currentTimeMillis() - sourceStart) / 1000;
                log.error("📡 {} — FAILED after ({}s): {}", source, duration, e.getMessage());
                totalSourcesFailed++;
            }
        }

        long runDuration = (System.currentTimeMillis() - runStart) / 1000;
        log.info("═══════════════════════════════════════════════════════");
        log.info("📊 RUN COMPLETE ({}s)", runDuration);
        log.info("   Sources: {}/{} succeeded", totalSourcesProcessed, scrapers.size());
        log.info("   Jobs: {} scraped → {} after filter → {} extracted", totalJobsScraped, totalJobsAfterFilter, totalExtracted);
        log.info("   Saved: {} jobs, {} users notified", totalSaved, notifiedCache.size());
        log.info("═══════════════════════════════════════════════════════");
    }

    private record SourceResult(int scraped, int afterFilter, int extracted, int saved) {}

    private SourceResult processScraper(JobScraper scraper, List<Alert> alerts,
                                         Map<UUID, ResumeProfileResponse> resumeCache) {
        String source = scraper.getSource();
        int totalScraped = 0;
        int totalAfterFilter = 0;
        int totalExtracted = 0;
        int totalSaved = 0;

        Set<String> supportedLocations = scraper.supportedLocations();

        for (int i = 0; i < alerts.size(); i++) {
            Alert alert = alerts.get(i);

            if (!supportedLocations.isEmpty() && !matchesSupportedLocation(alert.getLocation(), supportedLocations)) {
                log.debug("[{}] Skipping alert — location '{}' not in supported: {}", source, alert.getLocation(), supportedLocations);
                continue;
            }

            try {
                if (i > 0) {
                    long delay = 3000 + (long) (Math.random() * 5000);
                    log.info("[{}] Rate-limit delay {}ms before next alert", source, delay);
                    sleep(delay);
                }

                log.info("[{}] ({}/{}) Scraping for keyword='{}', location='{}'",
                        source, i + 1, alerts.size(), alert.getKeywords(), alert.getLocation());

                // ── PHASE 1: SCRAPE ──────────────────────────────────────────
                List<ScrapedJob> rawJobs = scraper.scrape(alert.getKeywords(), alert.getLocation());
                totalScraped += rawJobs.size();
                log.info("[{}] Phase 1: {} raw jobs scraped", source, rawJobs.size());

                // ── PHASE 2: DEDUPLICATE + PRE-FILTER ────────────────────────
                List<ScrapedJob> filteredJobs = dedupeAndFilter(rawJobs, alert, source);
                totalAfterFilter += filteredJobs.size();
                log.info("[{}] Phase 2: {} jobs after dedupe + pre-filter ({} eliminated)",
                        source, filteredJobs.size(), rawJobs.size() - filteredJobs.size());

                if (filteredJobs.isEmpty()) continue;

                // ── PHASE 3: DETERMINISTIC EXTRACT + PHASE 4: SCORE ──────────
                ResumeProfileResponse resumeProfile = resumeCache.get(alert.getUser().getId());
                List<String> userSkills = getUserSkills(resumeProfile);

                for (ScrapedJob job : filteredJobs) {
                    // Skip if user already has this job
                    if (userJobService.userHasJob(alert.getUser().getId(), job.getExternalJobId(), source)) {
                        continue;
                    }

                    // Deterministic extraction (no AI, <1ms)
                    DeterministicJobExtractor.JobProfile jobProfile =
                            DeterministicJobExtractor.extract(job, userSkills);

                    // Deterministic scoring
                    boolean saved = scoreJob(job, source, alert, resumeProfile, jobProfile, userSkills);
                    if (saved) {
                        totalExtracted++;
                        totalSaved++;
                    }
                }

            } catch (Exception e) {
                log.error("[{}] Error processing alert '{}': {}", source, alert.getKeywords(), e.getMessage());
            }
        }

        return new SourceResult(totalScraped, totalAfterFilter, totalExtracted, totalSaved);
    }

    private List<String> getUserSkills(ResumeProfileResponse resumeProfile) {
        if (resumeProfile == null || resumeProfile.getSkills() == null || resumeProfile.getSkills().isBlank()) {
            return List.of();
        }
        return Arrays.asList(resumeProfile.getSkills().split(",\\s*"));
    }

    private boolean matchesSupportedLocation(String alertLocation, Set<String> supportedLocations) {
        if (alertLocation == null || alertLocation.isBlank()) {
            return true;
        }
        String locLower = alertLocation.toLowerCase().trim();
        return supportedLocations.stream()
                .anyMatch(s -> locLower.contains(s.toLowerCase()) || s.toLowerCase().contains(locLower));
    }

    // ── PHASE 2: Deduplicate + Pre-filter ──────────────────────────────────

    private List<ScrapedJob> dedupeAndFilter(List<ScrapedJob> jobs, Alert alert, String source) {
        return jobs.stream()
                .filter(job -> isWithinAgeWindow(job))
                .filter(job -> matchesAlertKeyword(job, alert))
                .filter(job -> !isAlreadyRejected(job, source))
                .toList();
    }

    private boolean isWithinAgeWindow(ScrapedJob job) {
        if (job.getPostedAt() == null) {
            log.debug("[AgeFilter] Rejecting job — no postedAt date: {}", job.getTitle());
            return false;
        }
        long hours = Duration.between(job.getPostedAt(), LocalDateTime.now()).toHours();
        if (hours > MAX_JOB_AGE_HOURS) {
            log.debug("[AgeFilter] Rejecting job — posted {}h ago: {}", hours, job.getTitle());
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

    // ── PHASE 3+4: Deterministic Extract + Score ───────────────────────────

    private boolean scoreJob(ScrapedJob job, String source, Alert alert,
                              ResumeProfileResponse resumeProfile,
                              DeterministicJobExtractor.JobProfile jobProfile,
                              List<String> userSkills) {
        String jobKey = source + "_" + job.getExternalJobId();

        // Skill matching (deterministic)
        SkillMatcher.SkillMatchResult skillMatch = SkillMatcher.match(userSkills, job.getDescription());

        // Build candidate profile from resume
        RuleEngine.CandidateProfile candidate = buildCandidateProfile(resumeProfile);

        // Build job profile for rule engine
        RuleEngine.JobProfile ruleJob = RuleEngine.JobProfile.builder()
                .title(jobProfile.title() != null ? jobProfile.title() : job.getTitle())
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

        // Score with description match (replaces aiSemantic)
        RuleEngine.ScoreResult ruleResult = RuleEngine.score(candidate, ruleJob, alertData, skillMatch);

        log.info("[{}] Score: {}/100 for '{}' — skills {}/{} matched — {}",
                source, ruleResult.getScore(), job.getTitle(),
                skillMatch.matchCount(), skillMatch.totalSkills(),
                ruleResult.getReason());

        return handleScoreResult(job, source, alert, ruleResult.getScore(),
                ruleResult.getReason(), ruleResult.getMatchedSkills(), ruleResult.getMissingSkills());
    }

    private boolean handleScoreResult(ScrapedJob job, String source, Alert alert,
                                       int score, String reason,
                                       List<String> matchedSkills, List<String> missingSkills) {
        String jobKey = source + "_" + job.getExternalJobId();
        String userKey = alert.getUser().getId().toString();

        // Save job to jobs table
        Job savedJob = saveNewJob(job, source);

        jobCache.put(jobKey, new CacheEntry(score >= SCORE_THRESHOLD ? "accepted" : "low_score", System.currentTimeMillis()));

        // Always create UserJob entry — score is a ranking signal
        Map<String, CacheEntry> userNotified = notifiedCache.computeIfAbsent(userKey, k -> new ConcurrentHashMap<>());
        if (!userNotified.containsKey(jobKey)) {
            userJobService.saveUserJob(alert.getUser(), savedJob, score);

            if (score >= SCORE_THRESHOLD) {
                log.info("[{}] ✅ ACCEPTED (score {}/100): {} — {}", source, score, job.getTitle(), reason);
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
            } else {
                log.info("[{}] 📋 SAVED (score {}/100): {} — {}", source, score, job.getTitle(), reason);
            }

            userNotified.put(jobKey, new CacheEntry("notified", System.currentTimeMillis()));
            return true;
        }
        return false;
    }

    private Job saveNewJob(ScrapedJob job, String source) {
        Optional<Job> existing = jobService.getJobBySourceAndExternalId(source, job.getExternalJobId());
        if (existing.isPresent()) {
            return existing.get();
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
        return savedJob;
    }

    private RuleEngine.CandidateProfile buildCandidateProfile(ResumeProfileResponse resumeProfile) {
        return RuleEngine.CandidateProfile.builder()
                .level(resumeProfile != null && resumeProfile.getLevel() != null ? resumeProfile.getLevel() : "mid")
                .workPreference(resumeProfile != null && resumeProfile.getWorkPreference() != null ? resumeProfile.getWorkPreference() : "any")
                .preferredRoles(resumeProfile != null && resumeProfile.getPreferredRoles() != null
                        ? Arrays.asList(resumeProfile.getPreferredRoles().split(",\\s*"))
                        : List.of())
                .skills(resumeProfile != null && resumeProfile.getSkills() != null
                        ? Arrays.asList(resumeProfile.getSkills().split(",\\s*"))
                        : List.of())
                .tools(List.of())
                .cloudSkills(List.of())
                .location(null)
                .build();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
