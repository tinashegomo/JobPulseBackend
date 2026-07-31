package com.TinasheGomo.JobPulse.service;

import com.TinasheGomo.JobPulse.config.AiConfig;
import com.TinasheGomo.JobPulse.dto.resume.ResumeAnalysisResponse;
import com.TinasheGomo.JobPulse.util.AiModels;
import com.TinasheGomo.JobPulse.util.JsonParser;
import com.TinasheGomo.JobPulse.util.RetryUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ResumeAnalyzer {

    private final AiConfig aiConfig;
    private final RestTemplate aiRestTemplate;

    private static final List<String> VALID_LEVELS = List.of(
            "entry", "junior", "mid", "senior", "lead", "principal", "manager");

    private static final List<String> VALID_WORK_PREF = List.of(
            "remote", "hybrid", "onsite", "any");

    private static final String RESUME_PROMPT = """
            Extract a structured candidate profile from this resume. Be precise and specific.

            Resume text:
            %s

            Return ONLY raw JSON (no markdown, no code fences) in this exact shape:
            {
              "name": "full name or null",
              "title": "professional title (e.g. Full Stack Developer)",
              "yearsExperience": <number or null>,
              "level": "entry|junior|mid|senior|lead|principal|manager",
              "skills": ["Java", "Spring Boot", "React", "SQL"],
              "tools": ["Docker", "Git", "AWS"],
              "languages": ["JavaScript", "Python"],
              "frameworks": ["React", "Express"],
              "cloudSkills": ["Firebase", "AWS"],
              "preferredRoles": ["Software Engineer", "Backend Developer"],
              "avoidRoles": ["Senior", "Lead", "Manager"],
              "education": "Computer Engineering",
              "location": "Zimbabwe",
              "workPreference": "remote|hybrid|onsite|any",
              "highlights": ["2-3 key career highlights"]
            }

            Rules:
            - level must be one of: entry, junior, mid, senior, lead, principal, manager
            - workPreference must be one of: remote, hybrid, onsite, any
            - yearsExperience: estimate from work history (0-2 = junior, 3-5 = mid, 6-9 = senior, 10+ = lead/principal)
            - preferredRoles: roles that match their title and skills
            - avoidRoles: seniority levels above their current level (a junior should avoid senior/lead/principal/manager)
            - skills: core technical skills ONLY (not tools, not soft skills)
            - cloudSkills: cloud platforms and services they know""";

    public ResumeAnalysisResponse analyze(String resumeText) {
        String apiKey = aiConfig.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("AI API key not configured");
        }

        String prompt = String.format(RESUME_PROMPT, resumeText);
        Exception lastError = null;

        for (String model : AiModels.MODELS) {
            try {
                System.out.printf("[ResumeAnalyzer] Trying model: %s%n", model);

                String rawText = RetryUtil.withRetry(
                        () -> callAi(apiKey, model, prompt),
                        "ResumeAnalyzer:" + model);

                if (rawText == null || rawText.isBlank()) {
                    throw new RuntimeException("Empty response from model");
                }

                JsonNode parsed = JsonParser.parseAiJson(rawText);
                System.out.printf("[ResumeAnalyzer] Success with model: %s%n", model);
                return normalizeProfile(parsed);

            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
                throw new RuntimeException("AI API authentication failed", e);
            } catch (Exception e) {
                lastError = e;
                System.out.printf("[ResumeAnalyzer] Model %s failed: %s%n", model, e.getMessage());
            }
        }

        throw new RuntimeException(
                "All models failed. Last error: " + (lastError != null ? lastError.getMessage() : "unknown"));
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

        ResponseEntity<String> response = aiRestTemplate.exchange(url, HttpMethod.POST, request, String.class);

        JsonNode responseJson = JsonParser.parseAiJson(response.getBody());
        return responseJson
                .path("choices")
                .path(0)
                .path("message")
                .path("content")
                .asText("");
    }

    private ResumeAnalysisResponse normalizeProfile(JsonNode p) {
        return ResumeAnalysisResponse.builder()
                .name(p.has("name") && !p.get("name").isNull() ? p.get("name").asText() : null)
                .title(p.has("title") && !p.get("title").isNull() ? p.get("title").asText() : null)
                .yearsExperience(p.has("yearsExperience") && p.get("yearsExperience").isNumber()
                        ? p.get("yearsExperience").asInt() : null)
                .level(VALID_LEVELS.contains(p.path("level").asText("mid"))
                        ? p.get("level").asText("mid") : "mid")
                .skills(extractList(p, "skills"))
                .tools(extractList(p, "tools"))
                .languages(extractList(p, "languages"))
                .frameworks(extractList(p, "frameworks"))
                .cloudSkills(extractList(p, "cloudSkills"))
                .preferredRoles(extractList(p, "preferredRoles"))
                .avoidRoles(extractList(p, "avoidRoles"))
                .education(p.has("education") && !p.get("education").isNull()
                        ? p.get("education").asText() : null)
                .location(p.has("location") && !p.get("location").isNull()
                        ? p.get("location").asText() : null)
                .workPreference(VALID_WORK_PREF.contains(p.path("workPreference").asText("any"))
                        ? p.get("workPreference").asText("any") : "any")
                .highlights(extractList(p, "highlights"))
                .build();
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
