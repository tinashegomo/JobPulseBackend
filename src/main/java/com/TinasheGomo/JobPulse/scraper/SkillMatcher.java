package com.TinasheGomo.JobPulse.scraper;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic skill matching — matches user's skills against job description text.
 * No AI, pure keyword matching with partial matching support.
 */
public class SkillMatcher {

    private SkillMatcher() {}

    public record SkillMatchResult(
            List<String> matchedSkills,
            List<String> missingSkills,
            double matchPercentage,
            int matchCount,
            int totalSkills
    ) {}

    private static final Map<String, List<String>> SKILL_VARIANTS = Map.ofEntries(
            Map.entry("react", List.of("react", "reactjs", "react.js", "react js")),
            Map.entry("vue", List.of("vue", "vuejs", "vue.js", "vue js", "vue3", "vue 3")),
            Map.entry("angular", List.of("angular", "angularjs", "angular.js", "angular 2+")),
            Map.entry("node", List.of("node", "nodejs", "node.js", "node js", "express")),
            Map.entry("python", List.of("python", "python3", "python 3")),
            Map.entry("java", List.of("java", "java se", "java ee", "spring", "spring boot", "springboot")),
            Map.entry("javascript", List.of("javascript", "javascript es6", "es6", "es2015")),
            Map.entry("typescript", List.of("typescript", "ts")),
            Map.entry("sql", List.of("sql", "mysql", "postgresql", "postgres", "sqlite", "mssql")),
            Map.entry("nosql", List.of("nosql", "mongodb", "redis", "cassandra", "dynamodb", "dynamo")),
            Map.entry("docker", List.of("docker", "dockerfile", "docker-compose", "docker compose")),
            Map.entry("kubernetes", List.of("kubernetes", "k8s", "kubectl", "helm")),
            Map.entry("aws", List.of("aws", "amazon web services", "s3", "ec2", "lambda", "cloudfront")),
            Map.entry("gcp", List.of("gcp", "google cloud", "google cloud platform", "bigquery", "cloud run")),
            Map.entry("azure", List.of("azure", "microsoft azure", "azure devops")),
            Map.entry("git", List.of("git", "github", "gitlab", "bitbucket")),
            Map.entry("ci/cd", List.of("ci/cd", "cicd", "ci cd", "jenkins", "github actions", "gitlab ci", "circleci", "travis")),
            Map.entry("terraform", List.of("terraform", "infrastructure as code", "iac")),
            Map.entry("graphql", List.of("graphql", "graph ql", "apollo", "relay")),
            Map.entry("rest", List.of("rest", "restful", "rest api", "restapi", "http api")),
            Map.entry("microservices", List.of("microservice", "microservices", "service mesh", "grpc")),
            Map.entry("linux", List.of("linux", "unix", "ubuntu", "centos", "debian")),
            Map.entry("html", List.of("html", "html5", "html 5")),
            Map.entry("css", List.of("css", "css3", "css 3", "scss", "sass", "less", "tailwind", "bootstrap")),
            Map.entry("flutter", List.of("flutter", "dart")),
            Map.entry("swift", List.of("swift", "swiftui", "swift ui")),
            Map.entry("kotlin", List.of("kotlin")),
            Map.entry("react native", List.of("react native", "react-native")),
            Map.entry("machine learning", List.of("machine learning", "ml", "deep learning", "neural network")),
            Map.entry("data science", List.of("data science", "data scientist", "pandas", "numpy", "scikit-learn")),
            Map.entry("tensorflow", List.of("tensorflow", "tf", "keras")),
            Map.entry("pytorch", List.of("pytorch", "torch")),
            Map.entry("figma", List.of("figma")),
            Map.entry("photoshop", List.of("photoshop", "adobe photoshop")),
            Map.entry("postman", List.of("postman")),
            Map.entry("jira", List.of("jira", "atlassian")),
            Map.entry("agile", List.of("agile", "scrum", "kanban", "sprint"))
    );

    /**
     * Match user's skills against job description text.
     * Returns matched skills (found in description) and missing skills (not found).
     */
    public static SkillMatchResult match(List<String> userSkills, String description) {
        if (userSkills == null || userSkills.isEmpty()) {
            return new SkillMatchResult(List.of(), List.of(), 0.0, 0, 0);
        }

        if (description == null || description.isBlank()) {
            return new SkillMatchResult(List.of(), new ArrayList<>(userSkills), 0.0, 0, userSkills.size());
        }

        String lower = description.toLowerCase();
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (String skill : userSkills) {
            if (skill == null || skill.isBlank()) continue;

            String skillLower = skill.toLowerCase().trim();

            if (skillMatchesDescription(skillLower, lower)) {
                matched.add(skill);
            } else {
                missing.add(skill);
            }
        }

        int total = userSkills.size();
        int matchCount = matched.size();
        double percentage = total > 0 ? (double) matchCount / total * 100 : 0;

        return new SkillMatchResult(matched, missing, percentage, matchCount, total);
    }

    private static boolean skillMatchesDescription(String skillLower, String descriptionLower) {
        // Direct match
        if (descriptionLower.contains(skillLower)) return true;

        // Check variant mappings
        for (Map.Entry<String, List<String>> entry : SKILL_VARIANTS.entrySet()) {
            String canonical = entry.getKey();
            List<String> variants = entry.getValue();

            // If this skill is a variant of a canonical skill
            if (variants.contains(skillLower)) {
                // Check if any variant matches
                for (String variant : variants) {
                    if (descriptionLower.contains(variant)) return true;
                }
                // Also check if the canonical form matches
                if (descriptionLower.contains(canonical)) return true;
            }

            // If this skill IS the canonical form, check all variants
            if (skillLower.equals(canonical)) {
                for (String variant : variants) {
                    if (descriptionLower.contains(variant)) return true;
                }
            }
        }

        // Partial match: check if all parts of multi-word skill are present
        String[] parts = skillLower.split("\\s+");
        if (parts.length > 1) {
            boolean allFound = true;
            for (String part : parts) {
                if (part.length() >= 3 && !descriptionLower.contains(part)) {
                    allFound = false;
                    break;
                }
            }
            if (allFound) return true;
        }

        // Single word with length >= 4: check for substring match
        if (parts.length == 1 && skillLower.length() >= 4) {
            // Check if skill appears as part of a larger word
            // e.g., "react" should match "reactjs" but not "reaction"
            int idx = descriptionLower.indexOf(skillLower);
            while (idx >= 0) {
                // Check boundaries
                boolean beforeOk = idx == 0 || !Character.isLetter(descriptionLower.charAt(idx - 1));
                int afterIdx = idx + skillLower.length();
                boolean afterOk = afterIdx >= descriptionLower.length() || !Character.isLetter(descriptionLower.charAt(afterIdx));
                if (beforeOk && afterOk) return true;
                idx = descriptionLower.indexOf(skillLower, idx + 1);
            }
        }

        return false;
    }
}
