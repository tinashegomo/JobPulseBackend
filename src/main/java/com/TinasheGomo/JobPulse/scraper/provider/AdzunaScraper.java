package com.TinasheGomo.JobPulse.scraper.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class AdzunaScraper implements JobScraper {

    private static final String ADZUNA_API = "https://api.adzuna.com/v1/api/jobs/us/search/1";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; JobPulseBot/1.0)";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getSource() {
        return "ADZUNA";
    }

    @Override
    public List<ScrapedJob> scrape(String keywords, String location) {
        String apiUrl = buildApiUrl(keywords, location);
        log.info("[Adzuna] 🌐 Fetching jobs from API: {}", apiUrl);
        long start = System.currentTimeMillis();
        List<ScrapedJob> jobs = new ArrayList<>();

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[Adzuna] HTTP {} ({} bytes)", response.statusCode(), response.body().length());

            if (response.statusCode() / 100 != 2) {
                log.warn("[Adzuna] Non-2xx status: {} — free API may require app_id/app_key", response.statusCode());
                return jobs;
            }

            JsonNode rootNode = objectMapper.readTree(response.body());
            JsonNode results = rootNode.get("results");

            if (results == null || !results.isArray()) {
                log.warn("[Adzuna] No results array in response");
                return jobs;
            }

            log.info("[Adzuna] Parsing {} results...", results.size());

            for (JsonNode jobNode : results) {
                String externalJobId = jobNode.has("id") ? String.valueOf(jobNode.get("id").asLong()) : "";
                String title = jobNode.has("title") ? jobNode.get("title").asText("") : "";
                String company = jobNode.has("company") ? jobNode.get("company").path("display_name").asText("") : "";
                String loc = jobNode.has("location") ? jobNode.get("location").path("display_name").asText("") : "";
                String jobUrl = jobNode.has("redirect_url") ? jobNode.get("redirect_url").asText("") : "";
                String description = jobNode.has("description") ? jobNode.get("description").asText("") : "";

                LocalDateTime postedAt = null;
                String postedText = "";
                if (jobNode.has("created")) {
                    postedText = jobNode.get("created").asText("");
                    if (!postedText.isEmpty()) {
                        try {
                            postedAt = OffsetDateTime.parse(postedText)
                                    .withOffsetSameInstant(ZoneOffset.UTC)
                                    .toLocalDateTime();
                        } catch (Exception e) {
                            log.debug("[Adzuna] Failed to parse date '{}': {}", postedText, e.getMessage());
                        }
                    }
                }

                if (!externalJobId.isEmpty() && !title.isEmpty()) {
                    jobs.add(ScrapedJob.builder()
                            .externalJobId(externalJobId)
                            .title(title)
                            .company(company)
                            .location(loc)
                            .jobUrl(jobUrl)
                            .postedText(postedText)
                            .postedAt(postedAt)
                            .tags(new ArrayList<>())
                            .description(description)
                            .build());
                }
            }

            log.info("[Adzuna] ✅ Parsed {} jobs", jobs.size());
        } catch (Exception e) {
            log.error("[Adzuna] ❌ Scrape failed: {}", e.getMessage());
        }

        long elapsed = (System.currentTimeMillis() - start) / 1000;
        log.info("[Adzuna] Done — {} jobs ({}s)", jobs.size(), elapsed);
        return jobs;
    }

    private String buildApiUrl(String keywords, String location) {
        StringBuilder url = new StringBuilder(ADZUNA_API);
        url.append("?results_per_page=50&content-type=application/json");
        if (keywords != null && !keywords.isBlank()) {
            url.append("&what=").append(keywords.replace(" ", "%20"));
        }
        if (location != null && !location.isBlank()) {
            url.append("&where=").append(location.replace(" ", "%20"));
        }
        return url.toString();
    }
}
