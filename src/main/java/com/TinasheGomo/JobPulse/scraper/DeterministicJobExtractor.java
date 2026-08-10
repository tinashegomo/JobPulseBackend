package com.TinasheGomo.JobPulse.scraper;

import com.TinasheGomo.JobPulse.scraper.provider.ScrapedJob;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic job profile extraction — no AI, pure keyword matching.
 * Extracts level, workType, roleCategory, requiredSkills, bonusSkills, location, agency flag.
 */
@Slf4j
public class DeterministicJobExtractor {

    private DeterministicJobExtractor() {}

    public record JobProfile(
            String title,
            String level,
            String workType,
            String roleCategory,
            List<String> requiredSkills,
            List<String> bonusSkills,
            String locationNormalized,
            boolean isRecruitingAgency,
            double confidence
    ) {}

    private static final List<String> VALID_LEVELS = List.of(
            "entry", "junior", "mid", "senior", "lead", "principal", "manager");

    private static final List<String> VALID_WORK = List.of(
            "remote", "hybrid", "onsite", "unknown");

    private static final List<String> VALID_ROLES = List.of(
            "backend", "frontend", "fullstack", "mobile", "devops", "data", "design", "other");

    private static final Set<String> AGENCY_KEYWORDS = Set.of(
            "recruiting", "staffing", "talent", "hiring", "recruitment", "recruiter");

    private static final Pattern BONUS_SECTION_PATTERN = Pattern.compile(
            "(?i)(?:preferred|nice.to.have|bonus|plus|desirable|ideal|not.required).{0,200}",
            Pattern.DOTALL);

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "in", "on", "of", "for", "and", "or", "with", "is", "at",
            "to", "be", "we", "you", "our", "your", "this", "that", "are", "was", "were",
            "will", "can", "may", "should", "must", "have", "has", "had", "do", "does",
            "from", "by", "as", "or", "not", "but", "if", "so", "yet", "all", "any",
            "each", "every", "some", "no", "than", "too", "very", "just", "about");

    public static JobProfile extract(ScrapedJob job, List<String> userSkills) {
        String title = job.getTitle() != null ? job.getTitle() : "";
        String company = job.getCompany() != null ? job.getCompany() : "";
        String location = job.getLocation() != null ? job.getLocation() : "";
        String description = job.getDescription() != null ? job.getDescription() : "";
        String plainDescription = description.replaceAll("<[^>]+>", " ");

        String level = extractLevel(title, plainDescription);
        String workType = extractWorkType(plainDescription);
        String roleCategory = extractRoleCategory(title);
        String locationNormalized = extractLocation(location, workType);
        boolean isRecruitingAgency = detectAgency(company, plainDescription);

        List<String> requiredSkills = extractSkills(plainDescription, userSkills, false);
        List<String> bonusSkills = extractSkills(plainDescription, userSkills, true);

        double confidence = calculateConfidence(level, workType, roleCategory, requiredSkills, locationNormalized);

        return new JobProfile(
                title, level, workType, roleCategory,
                requiredSkills, bonusSkills, locationNormalized,
                isRecruitingAgency, confidence);
    }

    private static String extractLevel(String title, String description) {
        String combined = (title + " " + description).toLowerCase();

        if (containsAny(combined, "principal", "staff engineer", "distinguished")) return "principal";
        if (containsAny(combined, "director", "vp ", "vice president", "head of")) return "manager";
        if (containsAny(combined, "manager", "team lead", "tech lead", "engineering manager")) return "lead";
        if (containsAny(combined, "senior", "sr.", "sr ", "lead", "principal")) return "senior";
        if (containsAny(combined, "junior", "jr.", "jr ", "entry level", "entry-level", "associate", "intern")) return "entry";
        if (containsAny(combined, "mid level", "mid-level", "intermediate")) return "mid";

        // Default: try to infer from years mentioned
        Matcher yearMatcher = Pattern.compile("(\\d+)\\+?\\s*(?:years?|yrs?)").matcher(combined);
        if (yearMatcher.find()) {
            int years = Integer.parseInt(yearMatcher.group(1));
            if (years <= 2) return "entry";
            if (years <= 5) return "mid";
            if (years <= 9) return "senior";
            return "lead";
        }

        return "mid";
    }

    private static String extractWorkType(String description) {
        String lower = description.toLowerCase();

        // Strong remote signals
        if (containsAny(lower, "fully remote", "100% remote", "remote only", "remote-first",
                "remote friendly", "work from home", "wfh", "distributed team",
                "anywhere in the world", "no office", "location independent")) {
            return "remote";
        }

        // Strong onsite signals
        if (containsAny(lower, "on-site only", "onsite only", "in-office only",
                "must be in office", "office based", "office located",
                "relocate to", "relocation required")) {
            return "onsite";
        }

        // Hybrid signals
        if (containsAny(lower, "hybrid", "flexible", "mix of remote",
                "partially remote", "2-3 days in office", "3 days in office",
                "2 days in office", "some remote")) {
            return "hybrid";
        }

        // Weak remote signals
        if (containsAny(lower, "remote", "work from home")) {
            return "remote";
        }

        // Weak onsite signals
        if (containsAny(lower, "on-site", "onsite", "in-office")) {
            return "onsite";
        }

        return "unknown";
    }

    private static String extractRoleCategory(String title) {
        String lower = title.toLowerCase();

        if (containsAny(lower, "frontend", "front-end", "front end", "ui ", "ux ", "user interface", "react", "vue", "angular")) {
            return "frontend";
        }
        if (containsAny(lower, "backend", "back-end", "back end", "server", "api ", "microservice")) {
            return "backend";
        }
        if (containsAny(lower, "fullstack", "full-stack", "full stack", "full stack")) {
            return "fullstack";
        }
        if (containsAny(lower, "mobile", "ios", "android", "react native", "flutter", "swift", "kotlin")) {
            return "mobile";
        }
        if (containsAny(lower, "devops", "sre", "site reliability", "infrastructure", "platform", "cloud engineer")) {
            return "devops";
        }
        if (containsAny(lower, "data engineer", "data scientist", "machine learning", "ml engineer", "ai engineer", "analytics")) {
            return "data";
        }
        if (containsAny(lower, "design", "ux designer", "ui designer", "product designer")) {
            return "design";
        }

        // Default: check if title contains "engineer" or "developer"
        if (containsAny(lower, "engineer", "developer", "programmer", "software")) {
            return "backend"; // Default software roles to backend
        }

        return "other";
    }

    private static String extractLocation(String location, String workType) {
        if ("remote".equals(workType)) return "Remote";
        if (location == null || location.isBlank()) return "Unknown";

        String trimmed = location.trim();
        // If it already contains "Remote" or similar, keep it
        if (trimmed.toLowerCase().contains("remote")) return "Remote";

        return trimmed;
    }

    private static boolean detectAgency(String company, String description) {
        String companyLower = company.toLowerCase();
        for (String keyword : AGENCY_KEYWORDS) {
            if (companyLower.contains(keyword)) return true;
        }

        // Check description for agency signals
        String descLower = description.toLowerCase();
        if (containsAny(descLower, "our client", "the client", "we are hiring for",
                "our partner", "end client", "third party")) {
            return true;
        }

        return false;
    }

    private static List<String> extractSkills(String description, List<String> userSkills, boolean isBonus) {
        if (description == null || description.isBlank() || userSkills == null || userSkills.isEmpty()) {
            return List.of();
        }

        String lower = description.toLowerCase();
        Set<String> matched = new LinkedHashSet<>();

        // If looking for bonus skills, try to isolate the bonus/preferred section
        String searchArea = lower;
        if (isBonus) {
            Matcher bonusMatcher = BONUS_SECTION_PATTERN.matcher(lower);
            StringBuilder bonusArea = new StringBuilder();
            while (bonusMatcher.find()) {
                bonusArea.append(bonusMatcher.group()).append(" ");
            }
            if (bonusArea.length() > 0) {
                searchArea = bonusArea.toString();
            }
        }

        for (String skill : userSkills) {
            if (skill == null || skill.isBlank()) continue;
            String skillLower = skill.toLowerCase().trim();

            // Exact match
            if (searchArea.contains(skillLower)) {
                matched.add(skill);
                continue;
            }

            // Partial match (e.g., "react.js" matches "react", "spring boot" matches "spring")
            String[] skillParts = skillLower.split("\\s+");
            boolean allPartsFound = true;
            for (String part : skillParts) {
                if (part.length() >= 3 && !searchArea.contains(part)) {
                    allPartsFound = false;
                    break;
                }
            }
            if (allPartsFound && skillParts.length > 0) {
                matched.add(skill);
            }
        }

        return new ArrayList<>(matched);
    }

    private static double calculateConfidence(String level, String workType, String roleCategory,
                                               List<String> requiredSkills, String locationNormalized) {
        int fields = 0;
        int total = 5;

        if (!"mid".equals(level)) fields++; // mid is default, less confident
        if (!"unknown".equals(workType)) fields++;
        if (!"other".equals(roleCategory)) fields++;
        if (!requiredSkills.isEmpty()) fields++;
        if (!"Unknown".equals(locationNormalized)) fields++;

        return (double) fields / total;
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }
}
