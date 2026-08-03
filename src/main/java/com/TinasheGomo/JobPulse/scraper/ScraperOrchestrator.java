package com.TinasheGomo.JobPulse.scraper;

import com.TinasheGomo.JobPulse.entity.Alert;
import com.TinasheGomo.JobPulse.entity.Job;
import com.TinasheGomo.JobPulse.entity.JobProfile;
import com.TinasheGomo.JobPulse.dto.resumeprofile.ResumeProfileResponse;
import com.TinasheGomo.JobPulse.repository.AlertRepository;
import com.TinasheGomo.JobPulse.repository.JobProfileRepository;
import com.TinasheGomo.JobPulse.scraper.ai.JobExtractor;
import com.TinasheGomo.JobPulse.scraper.ai.SemanticChecker;
import com.TinasheGomo.JobPulse.scraper.provider.JobScraper;
import com.TinasheGomo.JobPulse.scraper.provider.ScrapedJob;
import com.TinasheGomo.JobPulse.service.JobService;
import com.TinasheGomo.JobPulse.service.ResumeProfileService;
import com.TinasheGomo.JobPulse.service.UserJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScraperOrchestrator {

    private static final int MAX_JOB_AGE_HOURS = 15;
    private static final int AI_SCORE_THRESHOLD = 50;
    private static final int SEMANTIC_UPPER_THRESHOLD = 85;
    private static final int SEMANTIC_LOWER_THRESHOLD = 50;
    private static final int BATCH_SIZE = 5;

    private static final Set<String> STOPWORDS = Set.of("a", "an", "the", "in", "on", "of", "for", "and", "or");

    private final AlertRepository alertRepository;
    private final JobService jobService;
    private final UserJobService userJobService;
    private final ResumeProfileService resumeProfileService;
    private final JobProfileRepository jobProfileRepository;
    private final List<JobScraper> scrapers;
    private final JobExtractor jobExtractor;
    private final SemanticChecker semanticChecker;
    private final NotificationService notificationService;

    private final Map<String, CacheEntry> jobCache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, CacheEntry>> notifiedCache = new ConcurrentHashMap<>();

    private record CacheEntry(String status, long timestamp) {}

    public void run() {
        log.info("═══════════════════════════════════════════════════════");
        log.info("🚀 OPTIMIZED SCRAPER RUN — {} scrapers registered", scrapers.size());
        log.info("   Pipeline: Scrape → Dedupe → Pre-filter → Extract → Score → Semantic (borderline only) → Notify");
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

        // Phase 0: Pre-load all resume profiles once (cache for this run)
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
        int totalScored = 0;
        int totalSemanticChecked = 0;
        int totalSaved = 0;

        for (JobScraper scraper : scrapers) {
            String source = scraper.getSource();
            long sourceStart = System.currentTimeMillis();

            log.info("───────────────────────────────────────────────────");
            log.info("📡 {} — scraping {} alerts", source, alerts.size());

            try {
                SourceResult result = processScraper(scraper, alerts, resumeCache);
                long duration = (System.currentTimeMillis() - sourceStart) / 1000;
                log.info("📡 {} — done ({}s): {} scraped → {} after filter → {} extracted → {} semantic checked → {} saved",
                        source, duration,
                        result.scraped, result.afterFilter, result.extracted, result.semanticChecked, result.saved);
                totalSourcesProcessed++;
                totalJobsScraped += result.scraped;
                totalJobsAfterFilter += result.afterFilter;
                totalExtracted += result.extracted;
                totalSemanticChecked += result.semanticChecked;
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
        log.info("   Jobs: {} scraped → {} after filter", totalJobsScraped, totalJobsAfterFilter);
        log.info("   AI: {} extracted, {} semantic checked", totalExtracted, totalSemanticChecked);
        log.info("   Saved: {} jobs, {} users notified", totalSaved, notifiedCache.size());
        log.info("═══════════════════════════════════════════════════════");
    }

    private record SourceResult(int scraped, int afterFilter, int extracted, int semanticChecked, int saved) {}

    private SourceResult processScraper(JobScraper scraper, List<Alert> alerts,
                                         Map<UUID, ResumeProfileResponse> resumeCache) {
        String source = scraper.getSource();
        int totalScraped = 0;
        int totalAfterFilter = 0;
        int totalExtracted = 0;
        int totalSemanticChecked = 0;
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

                // ── PHASE 3: EXTRACT JOB PROFILES (AI — once per new job) ────
                List<ScrapedJob> jobsNeedingExtraction = filterJobsNeedingExtraction(filteredJobs, source);
                int extracted = 0;
                if (!jobsNeedingExtraction.isEmpty()) {
                    extracted = extractJobProfiles(jobsNeedingExtraction, source);
                    totalExtracted += extracted;
                }
                log.info("[{}] Phase 3: {} jobs extracted ({} were already cached)",
                        source, extracted, filteredJobs.size() - jobsNeedingExtraction.size());

                // ── PHASE 4: SCORE (deterministic) + PHASE 5: SEMANTIC (borderline) ──
                ResumeProfileResponse resumeProfile = resumeCache.get(alert.getUser().getId());

                for (ScrapedJob job : filteredJobs) {
                    boolean saved = scoreAndMaybeSemantic(job, source, alert, resumeProfile);
                    if (saved) {
                        totalSaved++;
                    }
                }

            } catch (Exception e) {
                log.error("[{}] Error processing alert '{}': {}", source, alert.getKeywords(), e.getMessage());
            }
        }

        return new SourceResult(totalScraped, totalAfterFilter, totalExtracted, totalSemanticChecked, totalSaved);
    }

    private boolean matchesSupportedLocation(String alertLocation, Set<String> supportedLocations) {
        if (alertLocation == null || alertLocation.isBlank()) {
            return true; // No location specified = match all
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

    // ── PHASE 3: Extract Job Profiles (AI — batch, once per job) ──────────

    private List<ScrapedJob> filterJobsNeedingExtraction(List<ScrapedJob> jobs, String source) {
        // Get all external IDs that already have profiles
        List<String> externalIds = jobs.stream().map(ScrapedJob::getExternalJobId).toList();
        List<String> existingIds = jobProfileRepository.findExistingExternalIds(source, externalIds);
        Set<String> existingSet = new HashSet<>(existingIds);

        return jobs.stream()
                .filter(job -> !existingSet.contains(job.getExternalJobId()))
                .toList();
    }

    @Transactional
    private int extractJobProfiles(List<ScrapedJob> jobs, String source) {
        int extracted = 0;

        // Process in batches of BATCH_SIZE
        for (int batchStart = 0; batchStart < jobs.size(); batchStart += BATCH_SIZE) {
            int batchEnd = Math.min(batchStart + BATCH_SIZE, jobs.size());
            List<ScrapedJob> batch = jobs.subList(batchStart, batchEnd);

            log.info("[{}] Extracting batch {}/{} ({} jobs)...",
                    source, (batchStart / BATCH_SIZE) + 1,
                    (jobs.size() + BATCH_SIZE - 1) / BATCH_SIZE, batch.size());

            try {
                List<JobExtractor.JobProfile> profiles = jobExtractor.extractBatch(batch);

                for (int i = 0; i < batch.size(); i++) {
                    ScrapedJob job = batch.get(i);
                    JobExtractor.JobProfile profile = profiles.get(i);

                    if (profile == null) {
                        log.warn("[{}] Extraction returned null for '{}' — falling back to single extraction",
                                source, job.getTitle());
                        profile = jobExtractor.extract(job);
                    }

                    if (profile != null) {
                        saveJobProfile(job, profile, source);
                        extracted++;
                    }
                }
            } catch (Exception e) {
                log.error("[{}] Batch extraction failed: {} — falling back to individual", source, e.getMessage());
                for (ScrapedJob job : batch) {
                    try {
                        JobExtractor.JobProfile profile = jobExtractor.extract(job);
                        if (profile != null) {
                            saveJobProfile(job, profile, source);
                            extracted++;
                        }
                    } catch (Exception ex) {
                        log.error("[{}] Individual extraction failed for '{}': {}", source, job.getTitle(), ex.getMessage());
                    }
                }
            }
        }

        return extracted;
    }

    @Transactional
    private void saveJobProfile(ScrapedJob job, JobExtractor.JobProfile profile, String source) {
        Optional<Job> existingJob = jobService.getJobBySourceAndExternalId(source, job.getExternalJobId());
        if (existingJob.isEmpty()) {
            return; // Job not saved yet, profile will be saved when job is saved
        }

        Job savedJob = existingJob.get();

        // Check if profile already exists
        if (jobProfileRepository.existsBySourceAndExternalJobId(source, job.getExternalJobId())) {
            return;
        }

        JobProfile jobProfile = new JobProfile();
        jobProfile.setJob(savedJob);
        jobProfile.setSource(source);
        jobProfile.setExternalJobId(job.getExternalJobId());
        jobProfile.setTitle(profile.title());
        jobProfile.setLevel(profile.level());
        jobProfile.setWorkType(profile.workType());
        jobProfile.setRoleCategory(profile.roleCategory());
        jobProfile.setRequiredSkills(String.join(", ", profile.requiredSkills()));
        jobProfile.setBonusSkills(String.join(", ", profile.bonusSkills()));
        jobProfile.setLocationNormalized(profile.locationNormalized());
        jobProfile.setRecruitingAgency(profile.isRecruitingAgency());
        jobProfile.setConfidence(profile.confidence());

        jobProfileRepository.save(jobProfile);

        // Mark job as extracted
        savedJob.setProfileExtracted(true);
        jobService.saveJob(savedJob);
    }

    // ── PHASE 4+5: Score + Semantic Check ──────────────────────────────────

    private boolean scoreAndMaybeSemantic(ScrapedJob job, String source, Alert alert,
                                           ResumeProfileResponse resumeProfile) {
        String jobKey = source + "_" + job.getExternalJobId();
        String userKey = alert.getUser().getId().toString();

        // Skip if already rejected for this user
        CacheEntry cached = jobCache.get(jobKey);
        if (cached != null && "rejected".equals(cached.status())) {
            return false;
        }

        // Skip if already exists in DB
        Optional<Job> existingJob = jobService.getJobBySourceAndExternalId(source, job.getExternalJobId());
        if (existingJob.isPresent()) {
            return false;
        }

        // Get or create job profile
        JobProfile jobProfile = jobProfileRepository
                .findBySourceAndExternalJobId(source, job.getExternalJobId())
                .orElse(null);

        if (jobProfile == null) {
            // Profile not extracted yet — extract now (shouldn't happen often)
            log.warn("[{}] No cached profile for '{}' — extracting now", source, job.getTitle());
            try {
                JobExtractor.JobProfile extracted = jobExtractor.extract(job);
                if (extracted == null) {
                    log.warn("[{}] Extraction failed for '{}' — skipping", source, job.getTitle());
                    return false;
                }
                // Save job first, then profile
                Job savedJob = saveNewJob(job, source);
                saveJobProfile(job, extracted, source);
                jobProfile = jobProfileRepository
                        .findBySourceAndExternalJobId(source, job.getExternalJobId())
                        .orElse(null);
            } catch (Exception e) {
                log.error("[{}] Extraction failed for '{}': {}", source, job.getTitle(), e.getMessage());
                return false;
            }
        }

        if (jobProfile == null) {
            return false;
        }

        // ── PHASE 4: Rule Engine Score (deterministic, <1ms) ──
        if (resumeProfile == null || resumeProfile.getResumeText() == null || resumeProfile.getResumeText().isBlank()) {
            log.info("[{}] No resume for user {} — defaulting to score 50", source, alert.getUser().getId());
            return handleScoreResult(job, source, alert, 50, "no resume uploaded", List.of(), List.of());
        }

        RuleEngine.CandidateProfile candidate = buildCandidateProfile(resumeProfile);

        RuleEngine.JobProfile jobProfileForEngine = RuleEngine.JobProfile.builder()
                .title(jobProfile.getTitle() != null ? jobProfile.getTitle() : job.getTitle())
                .level(jobProfile.getLevel())
                .workType(jobProfile.getWorkType())
                .requiredSkills(jobProfile.getRequiredSkills() != null
                        ? Arrays.asList(jobProfile.getRequiredSkills().split(",\\s*"))
                        : List.of())
                .bonusSkills(jobProfile.getBonusSkills() != null
                        ? Arrays.asList(jobProfile.getBonusSkills().split(",\\s*"))
                        : List.of())
                .roleCategory(jobProfile.getRoleCategory())
                .locationNormalized(jobProfile.getLocationNormalized())
                .location(job.getLocation())
                .isRecruitingAgency(jobProfile.isRecruitingAgency())
                .build();

        RuleEngine.AlertData alertData = RuleEngine.AlertData.builder()
                .keyword(alert.getKeywords())
                .workType(alert.getWorkType())
                .location(alert.getLocation())
                .build();

        RuleEngine.ScoreResult ruleResult = RuleEngine.score(candidate, jobProfileForEngine, alertData, 0);

        log.info("[{}] Phase 4 — RuleEngine score: {}/100 for '{}' — {}",
                source, ruleResult.getScore(), job.getTitle(), ruleResult.getReason());

        // ── PHASE 5: Semantic Check (only for borderline: 50-85) ──
        int finalScore = ruleResult.getScore();
        String finalReason = ruleResult.getReason();
        List<String> matchedSkills = ruleResult.getMatchedSkills();
        List<String> missingSkills = ruleResult.getMissingSkills();

        if (finalScore >= SEMANTIC_LOWER_THRESHOLD && finalScore <= SEMANTIC_UPPER_THRESHOLD) {
            log.info("[{}] Phase 5 — Score {} is BORDERLINE ({}-{}) — running semantic check",
                    source, finalScore, SEMANTIC_LOWER_THRESHOLD, SEMANTIC_UPPER_THRESHOLD);

            try {
                List<String> requiredSkills = jobProfile.getRequiredSkills() != null
                        ? Arrays.asList(jobProfile.getRequiredSkills().split(",\\s*"))
                        : List.of();

                SemanticChecker.SemanticResult semanticResult = semanticChecker.checkWithProfile(
                        candidate,
                        jobProfile.getTitle() != null ? jobProfile.getTitle() : job.getTitle(),
                        job.getCompany(),
                        requiredSkills);

                if (semanticResult != null) {
                    // Blend semantic score into final: semantic adds 0-5 points
                    finalScore = Math.min(100, finalScore + semanticResult.score());
                    finalReason = finalReason + " + semantic: " + semanticResult.reason();
                    log.info("[{}] Phase 5 — Semantic: {}/5 → adjusted score: {}/100",
                            source, semanticResult.score(), finalScore);
                }
            } catch (Exception e) {
                log.warn("[{}] Semantic check failed: {}", source, e.getMessage());
            }
        } else if (finalScore > SEMANTIC_UPPER_THRESHOLD) {
            log.info("[{}] Phase 5 — Score {} > {} — ACCEPTED without semantic check", source, finalScore, SEMANTIC_UPPER_THRESHOLD);
        } else {
            log.info("[{}] Phase 5 — Score {} < {} — REJECTED without semantic check", source, finalScore, SEMANTIC_LOWER_THRESHOLD);
        }

        return handleScoreResult(job, source, alert, finalScore, finalReason, matchedSkills, missingSkills);
    }

    private boolean handleScoreResult(ScrapedJob job, String source, Alert alert,
                                       int score, String reason,
                                       List<String> matchedSkills, List<String> missingSkills) {
        String jobKey = source + "_" + job.getExternalJobId();
        String userKey = alert.getUser().getId().toString();

        if (score < AI_SCORE_THRESHOLD) {
            jobCache.put(jobKey, new CacheEntry("rejected", System.currentTimeMillis()));
            log.info("[{}] ❌ REJECTED (score {}/100): {} at {} — {}",
                    source, score, job.getTitle(), job.getCompany(), reason);
            return false;
        }

        log.info("[{}] ✅ ACCEPTED (score {}/100): {} — {}",
                source, score, job.getTitle(), reason);

        // Save job
        Job savedJob = saveNewJob(job, source);

        // Mark as extracted if profile exists
        if (jobProfileRepository.existsBySourceAndExternalJobId(source, job.getExternalJobId())) {
            savedJob.setProfileExtracted(true);
            jobService.saveJob(savedJob);
        }

        jobCache.put(jobKey, new CacheEntry("accepted", System.currentTimeMillis()));

        // Notify user
        Map<String, CacheEntry> userNotified = notifiedCache.computeIfAbsent(userKey, k -> new ConcurrentHashMap<>());
        if (!userNotified.containsKey(jobKey)) {
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

    private Job saveNewJob(ScrapedJob job, String source) {
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
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
