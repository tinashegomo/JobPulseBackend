package com.TinasheGomo.JobPulse.scraper.ai;

import com.TinasheGomo.JobPulse.config.AiConfig;
import com.TinasheGomo.JobPulse.scraper.RuleEngine.CandidateProfile;
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
public class SemanticChecker {

    private final AiConfig aiConfig;
    private final RestTemplate aiRestTemplate;

    private static final String PROMPT_TEMPLATE = """
            Quick check: does this job feel like a good fit for this candidate?

            Candidate: %s — skills: %s
            Job: %s at %s
            %s

            Rate alignment from 0-5:
            5 = perfect fit, exactly what they do
            4 = strong fit, very relevant
            3 = decent fit, related field
            2 = weak fit, tangentially related
            1 = poor fit, different field
            0 = no fit at all

            Return ONLY raw JSON: {"score": <0-5>, "reason": "<one short sentence>"}""";

    public record SemanticResult(int score, String reason) {}

    public SemanticResult check(CandidateProfile candidate, ScrapedJob job) {
        String apiKey = aiConfig.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[SemanticChecker] ❌ Missing AI API key — skipping");
            return null;
        }

        String plainDescription = (job.getDescription() != null ? job.getDescription() : "")
                .replaceAll("<[^>]+>", " ");
        if (plainDescription.length() > 2000) {
            plainDescription = plainDescription.substring(0, 2000);
        }

        String candSkills = candidate.getSkills() != null
                ? String.join(", ", candidate.getSkills())
                : "";
        String candTitle = candidate.getPreferredRoles() != null && !candidate.getPreferredRoles().isEmpty()
                ? candidate.getPreferredRoles().get(0) : "software developer";

        String descSnippet = plainDescription.isBlank()
                ? ""
                : "\nDescription snippet:\n" + plainDescription.substring(0, Math.min(1000, plainDescription.length()));

        String prompt = String.format(
                PROMPT_TEMPLATE,
                candTitle,
                candSkills,
                job.getTitle() != null ? job.getTitle() : "",
                job.getCompany() != null ? job.getCompany() : "Unknown",
                descSnippet);

        log.info("[SemanticChecker] Prompt size: {} chars, calling AI...", prompt.length());

        Exception lastError = null;

        for (String model : AiModels.MODELS) {
            long modelStart = System.currentTimeMillis();
            try {
                log.info("[SemanticChecker] 🔄 Trying model: {}", model);

                String rawText = RetryUtil.withRetry(
                        () -> callAi(apiKey, model, prompt),
                        "SemanticChecker:" + model);

                long modelDuration = (System.currentTimeMillis() - modelStart) / 1000;

                if (rawText == null || rawText.isBlank()) {
                    throw new RuntimeException("Empty response from model");
                }

                log.info("[SemanticChecker] Raw response: {}", rawText.length() > 200 ? rawText.substring(0, 200) + "..." : rawText);

                JsonNode parsed = JsonParser.parseAiJson(rawText);
                if (parsed == null || !parsed.has("score") || !parsed.get("score").isNumber()) {
                    log.warn("[SemanticChecker] Invalid response JSON: {}", parsed);
                    return null;
                }

                int score = Math.max(0, Math.min(5, Math.round(parsed.get("score").asInt())));
                String reason = parsed.has("reason") && !parsed.get("reason").isNull()
                        ? parsed.get("reason").asText() : "";

                log.info("[SemanticChecker] ✅ Success with model {} in {}s — score={}/5 — {}",
                        model, modelDuration, score, reason);
                return new SemanticResult(score, reason);

            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
                throw new RuntimeException("AI API authentication failed", e);
            } catch (Exception e) {
                lastError = e;
                log.warn("[SemanticChecker] ❌ Model {} failed after {}s: {}", model,
                        (System.currentTimeMillis() - modelStart) / 1000, e.getMessage());
            }
        }

        log.error("[SemanticChecker] ❌ All models exhausted. Last error: {}",
                lastError != null ? lastError.getMessage() : "unknown");
        return null;
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
}
