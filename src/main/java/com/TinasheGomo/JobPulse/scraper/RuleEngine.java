package com.TinasheGomo.JobPulse.scraper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic Rule Engine — scores jobs against a candidate profile
 * without using AI. Separates extraction (AI) from scoring (code).
 *
 * Scoring breakdown (100 points total):
 *   Work Type:     25 pts
 *   Role Match:    20 pts
 *   Seniority:     20 pts
 *   Skill Match:   20 pts
 *   Location:      10 pts
 *   AI Semantic:    5 pts (injected externally)
 *
 * Java port of jobpulse-watcher/scraper/ruleEngine.js
 */
public class RuleEngine {

    private static final List<String> SENIORITY_ORDER =
            Arrays.asList("entry", "junior", "mid", "senior", "lead", "principal", "manager");

    private static final Map<String, List<String>> ROLE_SKILL_MAP = new HashMap<>();

    static {
        ROLE_SKILL_MAP.put("backend", Arrays.asList("java", "python", "node", "go", "rust", "sql", "spring", "django", "express"));
        ROLE_SKILL_MAP.put("frontend", Arrays.asList("react", "vue", "angular", "javascript", "typescript", "css", "html"));
        ROLE_SKILL_MAP.put("fullstack", Arrays.asList("react", "node", "java", "python", "javascript", "typescript"));
        ROLE_SKILL_MAP.put("mobile", Arrays.asList("react native", "flutter", "swift", "kotlin", "dart"));
        ROLE_SKILL_MAP.put("devops", Arrays.asList("docker", "kubernetes", "aws", "terraform", "ci/cd", "jenkins"));
        ROLE_SKILL_MAP.put("data", Arrays.asList("python", "sql", "pandas", "spark", "tensorflow", "machine learning"));
    }

    // ── Result objects ────────────────────────────────────────────────────────

    @lombok.Data
    @lombok.Builder
    public static class ScoreResult {
        private int score;
        private Map<String, Integer> breakdown;
        private String reason;
        private List<String> matchedSkills;
        private List<String> missingSkills;
    }

    @lombok.Data
    @lombok.Builder
    public static class CandidateProfile {
        private String level;
        private String workPreference;
        private List<String> preferredRoles;
        private List<String> skills;
        private List<String> tools;
        private List<String> cloudSkills;
        private String location;
    }

    @lombok.Data
    @lombok.Builder
    public static class JobProfile {
        private String title;
        private String level;
        private String workType;
        private List<String> requiredSkills;
        private List<String> bonusSkills;
        private String roleCategory;
        private String locationNormalized;
        private String location;
        private boolean isRecruitingAgency;
    }

    @lombok.Data
    @lombok.Builder
    public static class AlertData {
        private String keyword;
        private String workType;
        private String location;
    }

    // ── Main scoring method ───────────────────────────────────────────────────

    public static ScoreResult score(CandidateProfile candidate, JobProfile job, AlertData alert,
                                     SkillMatcher.SkillMatchResult skillMatch) {
        Map<String, Integer> breakdown = new HashMap<>();
        int total = 0;

        // A. Work Type (25 points)
        int wt = workTypeScore(candidate, job, alert);
        breakdown.put("workType", wt);
        total += wt;

        // B. Role Match (20 points)
        int rm = roleMatchScore(candidate, job, alert);
        breakdown.put("roleMatch", rm);
        total += rm;

        // C. Seniority (20 points)
        int sr = seniorityScore(candidate, job);
        breakdown.put("seniority", sr);
        total += sr;

        // D. Skill Match from profile (20 points)
        SkillMatchResult sk = skillMatchScore(candidate, job);
        breakdown.put("skillMatch", sk.getScore());
        total += sk.getScore();

        // E. Location (10 points)
        int loc = locationScore(candidate, job, alert);
        breakdown.put("location", loc);
        total += loc;

        // F. Description Skill Match (0-5 points) — replaces aiSemantic
        int descMatch = descriptionMatchScore(skillMatch);
        breakdown.put("descriptionMatch", descMatch);
        total += descMatch;

        // Anti-bonus: recruiting agency penalty
        if (job.isRecruitingAgency()) {
            total -= 10;
            breakdown.put("agencyPenalty", -10);
        }

        // Clamp
        total = Math.max(0, Math.min(100, Math.round(total)));

        // Reason
        String reason = buildReason(breakdown, candidate, job, skillMatch);

        return ScoreResult.builder()
                .score(total)
                .breakdown(breakdown)
                .reason(reason)
                .matchedSkills(sk.getMatched())
                .missingSkills(sk.getMissing())
                .build();
    }

    // ── Work Type (25 pts) ────────────────────────────────────────────────────

    private static int workTypeScore(CandidateProfile candidate, JobProfile job, AlertData alert) {
        String pref = candidate.getWorkPreference() != null ? candidate.getWorkPreference().toLowerCase() : "any";
        String jobWT = job.getWorkType() != null ? job.getWorkType().toLowerCase() : "unknown";
        String alertWT = alert.getWorkType() != null ? alert.getWorkType().toLowerCase() : "";

        // If alert specifies work type, it's a hard filter
        if (!alertWT.isEmpty()) {
            if ("unknown".equals(jobWT)) return 0;
            if (jobNormMatch(jobWT, alertWT)) return 25;
            if ("remote".equals(alertWT) && "hybrid".equals(jobWT)) return 10;
            return -40; // Hard reject: onsite when they want remote
        }

        // No alert preference — use candidate preference
        switch (pref) {
            case "remote":
                if ("remote".equals(jobWT)) return 25;
                if ("hybrid".equals(jobWT)) return 10;
                if ("onsite".equals(jobWT)) return -40;
                return 0;
            case "hybrid":
                if ("hybrid".equals(jobWT)) return 25;
                if ("remote".equals(jobWT)) return 20;
                if ("onsite".equals(jobWT)) return 10;
                return 0;
            case "onsite":
                if ("onsite".equals(jobWT)) return 25;
                if ("hybrid".equals(jobWT)) return 20;
                return 5;
            default:
                // Any — neutral
                if ("remote".equals(jobWT)) return 20;
                if ("hybrid".equals(jobWT)) return 15;
                if ("onsite".equals(jobWT)) return 5;
                return 0;
        }
    }

    private static boolean jobNormMatch(String jobWT, String target) {
        if ("remote".equals(target)) return "remote".equals(jobWT);
        if ("hybrid".equals(target)) return "hybrid".equals(jobWT);
        if ("onsite".equals(target)) return "onsite".equals(jobWT);
        return false;
    }

    // ── Role Match (20 pts) ───────────────────────────────────────────────────

    private static int roleMatchScore(CandidateProfile candidate, JobProfile job, AlertData alert) {
        int score = 0;

        // Check preferred roles
        String titleLower = job.getTitle() != null ? job.getTitle().toLowerCase() : "";
        List<String> preferred = candidate.getPreferredRoles() != null
                ? candidate.getPreferredRoles().stream().map(r -> r.toLowerCase()).collect(Collectors.toList())
                : Collections.emptyList();

        for (String r : preferred) {
            if (titleLower.contains(r)) {
                score += 15;
                break;
            }
        }

        // Check role category alignment
        String roleCat = job.getRoleCategory() != null ? job.getRoleCategory().toLowerCase() : "other";
        List<String> skillsLower = candidate.getSkills() != null
                ? candidate.getSkills().stream().map(s -> s.toLowerCase()).collect(Collectors.toList())
                : Collections.emptyList();

        List<String> relevantSkills = ROLE_SKILL_MAP.getOrDefault(roleCat, Collections.emptyList());
        final int matchedCount = (int) skillsLower.stream()
                .filter(s -> relevantSkills.stream().anyMatch(rs -> s.contains(rs)))
                .count();

        if (matchedCount >= 3) score += 5;
        else if (matchedCount >= 1) score += 3;

        // Keyword alert match bonus
        if (alert.getKeyword() != null && !alert.getKeyword().isEmpty()) {
            String[] words = alert.getKeyword().toLowerCase().split("\\s+");
            for (String w : words) {
                if (w.length() >= 3 && titleLower.contains(w)) {
                    score += 5;
                    break;
                }
            }
        }

        return Math.min(20, score);
    }

    // ── Seniority (20 pts) ────────────────────────────────────────────────────

    private static int seniorityScore(CandidateProfile candidate, JobProfile job) {
        String candLevel = candidate.getLevel() != null ? candidate.getLevel().toLowerCase() : "mid";
        String jobLevel = job.getLevel() != null ? job.getLevel().toLowerCase() : "mid";

        int candIdx = SENIORITY_ORDER.indexOf(candLevel);
        int jobIdx = SENIORITY_ORDER.indexOf(jobLevel);

        if (candIdx == -1 || jobIdx == -1) return 10; // Unknown — neutral

        int diff = jobIdx - candIdx;

        // Perfect match or one level up
        if (diff == 0) return 20;
        if (diff == 1) return 15;

        // Two levels up (e.g., junior applying to senior)
        if (diff == 2) return 5;

        // Way above (junior → principal/manager)
        if (diff >= 3) return -30;

        // Job is below candidate level (senior applying to junior) — slight penalty
        if (diff == -1) return 12;
        if (diff <= -2) return 5;

        return 10;
    }

    // ── Skill Match (20 pts) ──────────────────────────────────────────────────

    @lombok.Data
    @lombok.Builder
    private static class SkillMatchResult {
        private int score;
        private List<String> matched;
        private List<String> missing;
    }

    private static SkillMatchResult skillMatchScore(CandidateProfile candidate, JobProfile job) {
        // Build candidate skill set from skills + tools + cloudSkills
        Set<String> candSkills = new HashSet<>();
        if (candidate.getSkills() != null) {
            candidate.getSkills().stream().map(String::toLowerCase).forEach(candSkills::add);
        }
        if (candidate.getTools() != null) {
            candidate.getTools().stream().map(String::toLowerCase).forEach(candSkills::add);
        }
        if (candidate.getCloudSkills() != null) {
            candidate.getCloudSkills().stream().map(String::toLowerCase).forEach(candSkills::add);
        }

        List<String> required = job.getRequiredSkills() != null
                ? job.getRequiredSkills().stream().map(String::toLowerCase).collect(Collectors.toList())
                : Collections.emptyList();
        List<String> bonus = job.getBonusSkills() != null
                ? job.getBonusSkills().stream().map(String::toLowerCase).collect(Collectors.toList())
                : Collections.emptyList();

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        // Required skills — weighted heavily
        double reqScore = 0;
        for (String skill : required) {
            String matchedSkill = findSkillMatch(skill, candSkills);
            if (matchedSkill != null) {
                matched.add(matchedSkill);
                reqScore += 1;
            } else {
                missing.add(skill);
            }
        }

        double reqPct = required.isEmpty() ? 1.0 : reqScore / required.size();

        // Bonus skills — lighter weight
        double bonusScore = 0;
        for (String skill : bonus) {
            String matchedSkill = findSkillMatch(skill, candSkills);
            if (matchedSkill != null) {
                matched.add(matchedSkill);
                bonusScore += 0.5;
            }
            // Don't add to missing for bonus skills
        }

        double totalExpected = required.size() + bonus.size() * 0.5;
        double totalMatched = reqScore + bonusScore;
        double pct = totalExpected > 0 ? totalMatched / totalExpected : 1;

        // Map to 0-20 points
        // 100% match = 20 pts, 50% = 12 pts, 0% = 0 pts
        int score = (int) Math.round(pct * 20);

        return SkillMatchResult.builder()
                .score(Math.min(20, score))
                .matched(matched)
                .missing(missing)
                .build();
    }

    private static String findSkillMatch(String jobSkill, Set<String> candSkills) {
        // Exact match
        if (candSkills.contains(jobSkill)) return jobSkill;

        // Partial match (e.g., "react.js" matches "react")
        for (String cs : candSkills) {
            if (cs.contains(jobSkill) || jobSkill.contains(cs)) return cs;
        }

        return null;
    }

    // ── Location (10 pts) ─────────────────────────────────────────────────────

    private static int locationScore(CandidateProfile candidate, JobProfile job, AlertData alert) {
        String alertLoc = alert.getLocation() != null ? alert.getLocation().toLowerCase() : "";
        String jobLoc = (job.getLocationNormalized() != null ? job.getLocationNormalized() : "")
                + (job.getLocation() != null ? job.getLocation() : "");
        jobLoc = jobLoc.toLowerCase();
        String candLoc = candidate.getLocation() != null ? candidate.getLocation().toLowerCase() : "";

        // Remote job = always good
        if ("remote".equals(job.getWorkType())) return 10;

        // No alert location specified — neutral
        if (alertLoc.isEmpty()) return 5;

        // Alert has location preference
        if (jobLoc.contains(alertLoc) || alertLoc.contains(jobLoc)) return 10;

        // Same country as candidate
        if (!candLoc.isEmpty()) {
            String[] candParts = candLoc.split(",");
            String lastPart = candParts.length > 0 ? candParts[candParts.length - 1].trim() : candLoc;
            if (jobLoc.contains(lastPart)) return 8;
        }

        // Different location, not remote
        return 0;
    }

    // ── Reason builder ────────────────────────────────────────────────────────

    private static String buildReason(Map<String, Integer> breakdown, CandidateProfile candidate,
                                       JobProfile job, SkillMatcher.SkillMatchResult skillMatch) {
        List<String> parts = new ArrayList<>();

        int seniorityScore = breakdown.getOrDefault("seniority", 0);
        int workTypeScore = breakdown.getOrDefault("workType", 0);
        int skillMatchScore = breakdown.getOrDefault("skillMatch", 0);
        int descMatchScore = breakdown.getOrDefault("descriptionMatch", 0);
        boolean hasAgencyPenalty = breakdown.containsKey("agencyPenalty");

        if (seniorityScore >= 15) {
            parts.add("good " + (candidate.getLevel() != null ? candidate.getLevel() : "mid") + " fit");
        } else if (seniorityScore <= 0) {
            parts.add("seniority mismatch");
        }

        if (workTypeScore >= 20) {
            parts.add((job.getWorkType() != null ? job.getWorkType() : "unknown") + " role");
        } else if (workTypeScore < 0) {
            parts.add((job.getWorkType() != null ? job.getWorkType() : "unknown") + " but you prefer remote");
        }

        if (skillMatchScore >= 15) {
            parts.add("strong skill match");
        } else if (skillMatchScore <= 5) {
            parts.add("missing key skills");
        }

        if (skillMatch != null) {
            parts.add(skillMatch.matchCount() + "/" + skillMatch.totalSkills() + " skills found");
        }

        if (hasAgencyPenalty) {
            parts.add("recruiting agency");
        }

        return parts.isEmpty() ? "mixed signals" : String.join(", ", parts);
    }

    /**
     * Description skill match score (0-5 points).
     * Based on how many of the user's skills appear in the job description.
     */
    private static int descriptionMatchScore(SkillMatcher.SkillMatchResult skillMatch) {
        if (skillMatch == null) return 0;
        double pct = skillMatch.matchPercentage();
        if (pct >= 80) return 5;
        if (pct >= 60) return 4;
        if (pct >= 40) return 3;
        if (pct >= 20) return 2;
        if (pct >= 10) return 1;
        return 0;
    }
}
