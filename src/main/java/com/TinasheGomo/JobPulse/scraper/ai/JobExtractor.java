package com.TinasheGomo.JobPulse.scraper.ai;

import com.TinasheGomo.JobPulse.config.AiConfig;
import com.TinasheGomo.JobPulse.scraper.provider.ScrapedJob;
import com.TinasheGomo.JobPulse.util.AiModels;
import com.TinasheGomo.JobPulse.util.JsonParser;
import com.TinasheGomo.JobPulse.util.RetryUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobExtractor {

    private final AiConfig aiConfig;
    private final RestTemplate aiRestTemplate;

    private static final List<String> VALID_LEVELS = List.of(
            "entry", "junior", "mid", "senior", "lead", "principal", "manager");

    private static final List<String> VALID_WORK = List.of(
            "remote", "hybrid", "onsite", "unknown");

    private static final List<String> VALID_ROLES = List.of(
            "backend", "frontend", "fullstack", "mobile", "devops", "data", "design", "other");

    private static final String PROMPT_TEMPLATE = """
            Extract structured information from this job posting. Be precise.

            Job:
            Title: %s
            Company: %s
            Location: %s
            %s

            Return ONLY raw JSON (no markdown, no code fences) in this exact shape:
            {
              "title": "cleaned job title",
              "level": "entry|junior|mid|senior|lead|principal|manager",
              "workType": "remote|hybrid|onsite|unknown",
              "requiredSkills": ["Java", "Spring Boot"],
              "bonusSkills": ["Docker", "Kubernetes"],
              "roleCategory": "backend|frontend|fullstack|mobile|devops|data|design|other",
              "seniorityKeywords": ["senior", "5+ years"],
              "locationNormalized": "Remote|City, Country|Unknown",
              "isRecruitingAgency": false,
              "confidence": 0.85
            }

            Rules:
            - level: infer from title and description keywords (junior/entry = 0-2yr, mid = 3-5yr, senior = 6+yr, lead/manager = people management)
            - workType: "remote" if explicitly remote or distributed team, "hybrid" if mix, "onsite" if office required, "unknown" if unclear
            - requiredSkills: hard skills the job REQUIRES (mentioned as required/mandatory)
            - bonusSkills: nice-to-have skills (mentioned as preferred/bonus)
            - roleCategory: best fit category
            - isRecruitingAgency: true if posting is from a staffing agency or has vague company info
            - confidence: how confident you are in this extraction (0-1), lower if description is vague""";

    public record JobProfile(
            String title,
            String level,
            String workType,
            List<String> requiredSkills,
            List<String> bonusSkills,
            String roleCategory,
            List<String> seniorityKeywords,
            String locationNormalized,
            boolean isRecruitingAgency,
            double confidence
    ) {}

    public JobProfile extract(ScrapedJob job) {
        String apiKey = aiConfig.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[JobExtractor] ❌ Missing AI API key — skipping extraction");
            return null;
        }

        String plainDescription = (job.getDescription() != null ? job.getDescription() : "")
                .replaceAll("<[^>]+>", " ");
        if (plainDescription.length() > 1500) {
            plainDescription = plainDescription.substring(0, 1500);
        }

        String descSection = plainDescription.isBlank()
                ? ""
                : "\nDescription:\n" + plainDescription;

        String prompt = String.format(
                PROMPT_TEMPLATE,
                job.getTitle() != null ? job.getTitle() : "",
                job.getCompany() != null ? job.getCompany() : "Unknown",
                job.getLocation() != null ? job.getLocation() : "Unknown",
                descSection);

        log.info("[JobExtractor] Prompt size: {} chars, calling AI...", prompt.length());

        Exception lastError = null;

        for (String model : AiModels.MODELS) {
            long modelStart = System.currentTimeMillis();
            try {
                log.info("[JobExtractor] 🔄 Trying model: {}", model);

                String rawText = RetryUtil.withRetry(
                        () -> callAi(apiKey, model, prompt),
                        "JobExtractor:" + model);

                long modelDuration = (System.currentTimeMillis() - modelStart) / 1000;

                if (rawText == null || rawText.isBlank()) {
                    throw new RuntimeException("Empty response from model");
                }

                log.info("[JobExtractor] Raw response ({} chars): {}",
                        rawText.length(),
                        rawText.length() > 200 ? rawText.substring(0, 200) + "..." : rawText);

                JsonNode parsed = JsonParser.parseAiJson(rawText);

                if (parsed == null || !parsed.has("title") || parsed.get("title").isNull()) {
                    log.warn("[JobExtractor] Invalid extraction JSON: {}", parsed);
                    return null;
                }

                log.info("[JobExtractor] ✅ Success with model {} in {}s — level={}, workType={}, role={}, confidence={}",
                        model, modelDuration,
                        parsed.path("level").asText("mid"),
                        parsed.path("workType").asText("unknown"),
                        parsed.path("roleCategory").asText("other"),
                        parsed.path("confidence").asDouble(0.5));
                return normalizeProfile(parsed, job);

            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
                throw new RuntimeException("AI API authentication failed", e);
            } catch (Exception e) {
                lastError = e;
                log.warn("[JobExtractor] ❌ Model {} failed after {}s: {}", model,
                        (System.currentTimeMillis() - modelStart) / 1000, e.getMessage());
            }
        }

        log.error("[JobExtractor] ❌ All models exhausted. Last error: {}",
                lastError != null ? lastError.getMessage() : "unknown");
        return null;
    }

    /**
     * Batch extract: sends multiple jobs in one AI call.
     * Returns a list of JobProfile in the same order as input jobs.
     * If a job fails extraction, its entry will be null.
     */
    public List<JobProfile> extractBatch(List<ScrapedJob> jobs) {
        String apiKey = aiConfig.getApiKey();
        if (apiKey == null || apiKey.isBlank() || jobs == null || jobs.isEmpty()) {
            return jobs.stream().map(j -> (JobProfile) null).toList();
        }

        // Build batch prompt with all jobs
        StringBuilder jobsSection = new StringBuilder();
        for (int i = 0; i < jobs.size(); i++) {
            ScrapedJob job = jobs.get(i);
            String desc = (job.getDescription() != null ? job.getDescription() : "")
                    .replaceAll("<[^>]+>", " ");
            if (desc.length() > 1500) desc = desc.substring(0, 1500);

            jobsSection.append(String.format("""
                    
                    Job #%d:
                    Title: %s
                    Company: %s
                    Location: %s
                    %s
                    """,
                    i + 1,
                    job.getTitle() != null ? job.getTitle() : "",
                    job.getCompany() != null ? job.getCompany() : "Unknown",
                    job.getLocation() != null ? job.getLocation() : "Unknown",
                    desc.isBlank() ? "" : "Description:\n" + desc
            ));
        }

        String prompt = String.format("""
                Extract structured information from these %d job postings. Be precise.

                %s

                Return ONLY raw JSON (no markdown, no code fences) — an array of objects, one per job:
                [
                  {
                    "jobIndex": 1,
                    "title": "cleaned job title",
                    "level": "entry|junior|mid|senior|lead|principal|manager",
                    "workType": "remote|hybrid|onsite|unknown",
                    "requiredSkills": ["Java", "Spring Boot"],
                    "bonusSkills": ["Docker"],
                    "roleCategory": "backend|frontend|fullstack|mobile|devops|data|design|other",
                    "locationNormalized": "Remote|City, Country|Unknown",
                    "isRecruitingAgency": false,
                    "confidence": 0.85
                  }
                ]

                Rules:
                - Return exactly %d objects in the array
                - jobIndex must match the Job # number (1-based)
                - level: infer from title and description keywords
                - workType: "remote" if explicitly remote, "hybrid" if mix, "onsite" if office, "unknown" if unclear
                - requiredSkills: hard skills the job REQUIRES
                - bonusSkills: nice-to-have skills
                - roleCategory: best fit category
                - isRecruitingAgency: true if posting is from a staffing agency
                - confidence: how confident you are (0-1)""",
                jobs.size(), jobsSection, jobs.size());

        log.info("[JobExtractor] Batch prompt size: {} chars for {} jobs, calling AI...", prompt.length(), jobs.size());

        Exception lastError = null;

        for (String model : AiModels.MODELS) {
            long modelStart = System.currentTimeMillis();
            try {
                log.info("[JobExtractor] 🔄 Trying model {} for batch of {} jobs...", model, jobs.size());

                String rawText = RetryUtil.withRetry(
                        () -> callAi(apiKey, model, prompt),
                        "JobExtractorBatch:" + model);

                long modelDuration = (System.currentTimeMillis() - modelStart) / 1000;

                if (rawText == null || rawText.isBlank()) {
                    throw new RuntimeException("Empty response from model");
                }

                JsonNode parsed = JsonParser.parseAiJson(rawText);

                if (parsed == null || !parsed.isArray()) {
                    log.warn("[JobExtractor] Batch response is not an array: {}", parsed);
                    // Fall back to individual extraction
                    return jobs.stream().map(this::extract).toList();
                }

                log.info("[JobExtractor] ✅ Batch success with model {} in {}s — {} profiles returned",
                        model, modelDuration, parsed.size());

                // Map by jobIndex
                Map<Integer, JsonNode> profileMap = new HashMap<>();
                for (JsonNode node : parsed) {
                    int idx = node.has("jobIndex") ? node.get("jobIndex").asInt(0) : 0;
                    if (idx > 0) profileMap.put(idx, node);
                }

                List<JobProfile> results = new ArrayList<>();
                for (int i = 0; i < jobs.size(); i++) {
                    JsonNode profileJson = profileMap.get(i + 1);
                    if (profileJson != null) {
                        results.add(normalizeProfile(profileJson, jobs.get(i)));
                    } else {
                        results.add(null);
                    }
                }
                return results;

            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
                throw new RuntimeException("AI API authentication failed", e);
            } catch (Exception e) {
                lastError = e;
                log.warn("[JobExtractor] ❌ Model {} batch failed after {}s: {}", model,
                        (System.currentTimeMillis() - modelStart) / 1000, e.getMessage());
            }
        }

        log.error("[JobExtractor] ❌ All models exhausted for batch. Falling back to individual extraction.");
        return jobs.stream().map(this::extract).toList();
    }

    private String callAi(String apiKey, String model, String prompt) {
        String url = aiConfig.getBaseUrl() + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        body.put("max_tokens", 2048);
        body.put("temperature", 0.1);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = aiRestTemplate.exchange(
                url, HttpMethod.POST, request, String.class);

        JsonNode responseJson = JsonParser.parseAiJson(response.getBody());
        return responseJson
                .path("choices")
                .path(0)
                .path("message")
                .path("content")
                .asText("");
    }

    private JobProfile normalizeProfile(JsonNode p, ScrapedJob job) {
        return new JobProfile(
                p.has("title") && !p.get("title").isNull() ? p.get("title").asText() : job.getTitle(),
                VALID_LEVELS.contains(p.path("level").asText("mid"))
                        ? p.get("level").asText("mid") : "mid",
                VALID_WORK.contains(p.path("workType").asText("unknown"))
                        ? p.get("workType").asText("unknown") : "unknown",
                extractList(p, "requiredSkills"),
                extractList(p, "bonusSkills"),
                VALID_ROLES.contains(p.path("roleCategory").asText("other"))
                        ? p.get("roleCategory").asText("other") : "other",
                extractList(p, "seniorityKeywords"),
                p.has("locationNormalized") && !p.get("locationNormalized").isNull()
                        ? p.get("locationNormalized").asText()
                        : (job.getLocation() != null ? job.getLocation() : "Unknown"),
                p.has("isRecruitingAgency") && p.get("isRecruitingAgency").asBoolean(false),
                p.has("confidence") && p.get("confidence").isNumber()
                        ? p.get("confidence").asDouble(0.5) : 0.5
        );
    }

    private List<String> extractList(JsonNode node, String field) {
        if (node.has(field) && node.get(field).isArray()) {
            List<String> result = new ArrayList<>();
            node.get(field).forEach(e -> result.add(e.asText()));
            return result;
        }
        return List.of();
    }
}
