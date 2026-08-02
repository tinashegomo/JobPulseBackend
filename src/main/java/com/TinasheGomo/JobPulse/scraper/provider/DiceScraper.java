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
public class DiceScraper implements JobScraper {

    private static final String DICE_API = "https://jobsearch-api.com/v1/search";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; JobPulseBot/1.0)";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getSource() {
        return "DICE";
    }

    @Override
    public List<ScrapedJob> scrape(String keywords, String location) {
        String apiUrl = buildApiUrl(keywords, location);
        log.info("[Dice] 🌐 Fetching jobs from API: {}", apiUrl);
        long start = System.currentTimeMillis();
        List<ScrapedJob> jobs = new ArrayList<>();

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[Dice] HTTP {} ({} bytes)", response.statusCode(), response.body().length());

            if (response.statusCode() / 100 != 2) {
                log.warn("[Dice] API returned {} — falling back to HTML scrape", response.statusCode());
                return scrapeHtml(keywords, location);
            }

            JsonNode rootNode = objectMapper.readTree(response.body());
            JsonNode results = rootNode.get("searchResults");

            if (results == null || !results.isArray()) {
                log.warn("[Dice] No searchResults array in response");
                return jobs;
            }

            log.info("[Dice] Parsing {} results...", results.size());

            for (JsonNode jobNode : results) {
                String externalJobId = jobNode.has("jobId") ? jobNode.get("jobId").asText("") : "";
                String title = jobNode.has("jobTitle") ? jobNode.get("jobTitle").asText("") : "";
                String company = jobNode.has("companyName") ? jobNode.get("companyName").asText("") : "";
                String loc = jobNode.has("jobLocation") ? jobNode.get("jobLocation").asText("") : "";
                String jobUrl = jobNode.has("jobUrl") ? jobNode.get("jobUrl").asText("") : "";
                String description = jobNode.has("jobDescription") ? jobNode.get("jobDescription").asText("") : "";

                LocalDateTime postedAt = null;
                String postedText = "";
                if (jobNode.has("datePosted")) {
                    postedText = jobNode.get("datePosted").asText("");
                    if (!postedText.isEmpty()) {
                        try {
                            postedAt = OffsetDateTime.parse(postedText)
                                    .withOffsetSameInstant(ZoneOffset.UTC)
                                    .toLocalDateTime();
                        } catch (Exception e) {
                            log.debug("[Dice] Failed to parse date '{}': {}", postedText, e.getMessage());
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

            log.info("[Dice] ✅ Parsed {} jobs from API", jobs.size());
        } catch (Exception e) {
            log.error("[Dice] ❌ API scrape failed: {}", e.getMessage());
        }

        long elapsed = (System.currentTimeMillis() - start) / 1000;
        log.info("[Dice] Done — {} jobs ({}s)", jobs.size(), elapsed);
        return jobs;
    }

    private List<ScrapedJob> scrapeHtml(String keywords, String location) {
        List<ScrapedJob> jobs = new ArrayList<>();
        try {
            String searchUrl = "https://www.dice.com/jobs";
            if (keywords != null && !keywords.isBlank()) {
                searchUrl += "?q=" + keywords.replace(" ", "+");
                if (location != null && !location.isBlank()) {
                    searchUrl += "&location=" + location.replace(" ", "+");
                }
            }

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                    .header("Accept", "text/html")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[Dice] HTML fallback HTTP {} ({} bytes)", response.statusCode(), response.body().length());

            if (response.statusCode() / 100 != 2) return jobs;

            String html = response.body();
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                    "<a[^>]*href=\"(/jobs/detail/[^\"]+)\"[^>]*>.*?<h3[^>]*>(.*?)</h3>.*?" +
                    "<span[^>]*class=\"[^\"]*company[^\"]*\"[^>]*>(.*?)</span>",
                    java.util.regex.Pattern.DOTALL
            ).matcher(html);

            while (matcher.find()) {
                String path = matcher.group(1).trim();
                String jobUrl = "https://www.dice.com" + path;
                String title = matcher.group(2).replaceAll("<[^>]+>", "").trim();
                String company = matcher.group(3).replaceAll("<[^>]+>", "").trim();

                if (title.isEmpty()) continue;

                jobs.add(ScrapedJob.builder()
                        .externalJobId(path.replaceAll("[^a-zA-Z0-9]", "_"))
                        .title(title)
                        .company(company)
                        .location(location != null ? location : "")
                        .jobUrl(jobUrl)
                        .postedText("")
                        .postedAt(null)
                        .tags(new ArrayList<>())
                        .description("")
                        .build());
            }

            log.info("[Dice] HTML fallback found {} jobs", jobs.size());
        } catch (Exception e) {
            log.error("[Dice] HTML fallback failed: {}", e.getMessage());
        }
        return jobs;
    }

    private String buildApiUrl(String keywords, String location) {
        StringBuilder url = new StringBuilder(DICE_API);
        url.append("?limit=50");
        if (keywords != null && !keywords.isBlank()) {
            url.append("&q=").append(keywords.replace(" ", "+"));
        }
        if (location != null && !location.isBlank()) {
            url.append("&location=").append(location.replace(" ", "+"));
        }
        return url.toString();
    }
}
