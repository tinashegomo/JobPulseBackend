package com.TinasheGomo.JobPulse.scraper.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class RemoteOkScraper implements JobScraper {

    private static final String REMOTE_OK_API = "https://remoteok.com/api";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; JobPulseBot/1.0)";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getSource() {
        return "REMOTEOK";
    }

    @Override
    public List<ScrapedJob> scrape(String keywords, String location) {
        log.info("[RemoteOK] 🌐 Fetching job feed from {}", REMOTE_OK_API);
        List<ScrapedJob> jobs = new ArrayList<>();

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(REMOTE_OK_API))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[RemoteOK] HTTP {} ({} bytes)", response.statusCode(), response.body().length());

            if (response.statusCode() / 100 != 2) {
                log.error("[RemoteOK] ❌ Fetch failed with status {}", response.statusCode());
                return jobs;
            }

            JsonNode rootNode = objectMapper.readTree(response.body());

            if (!rootNode.isArray() || rootNode.size() < 2) {
                log.warn("[RemoteOK] ⚠ API returned unexpected format (size={})", rootNode.size());
                return jobs;
            }

            log.info("[RemoteOK] Parsing {} items from JSON array...", rootNode.size() - 1);

            for (int i = 1; i < rootNode.size(); i++) {
                JsonNode jobNode = rootNode.get(i);

                String externalJobId = jobNode.has("id") ? String.valueOf(jobNode.get("id").asLong()) : "";
                String title = jobNode.has("position") ? jobNode.get("position").asText("") : "";
                String company = jobNode.has("company") ? jobNode.get("company").asText("") : "";
                String jobLocation = jobNode.has("location") ? jobNode.get("location").asText("Remote") : "Remote";
                String jobUrl = jobNode.has("url") ? jobNode.get("url").asText("") : "";
                if (jobUrl.isEmpty() && !externalJobId.isEmpty()) {
                    jobUrl = "https://remoteok.com/remote-jobs/" + externalJobId;
                }

                LocalDateTime postedAt = null;
                String postedText = jobNode.has("date") ? jobNode.get("date").asText("") : "";
                if (!postedText.isEmpty()) {
                    try {
                        postedAt = OffsetDateTime.parse(postedText)
                                .withOffsetSameInstant(ZoneOffset.UTC)
                                .toLocalDateTime();
                    } catch (Exception e) {
                        log.debug("[RemoteOK] Failed to parse date '{}': {}", postedText, e.getMessage());
                    }
                }

                List<String> tags = new ArrayList<>();
                if (jobNode.has("tags") && jobNode.get("tags").isArray()) {
                    for (JsonNode tag : jobNode.get("tags")) {
                        tags.add(tag.asText());
                    }
                }

                String description = jobNode.has("description") ? jobNode.get("description").asText("") : "";

                if (!externalJobId.isEmpty() && !title.isEmpty()) {
                    ScrapedJob job = ScrapedJob.builder()
                            .externalJobId(externalJobId)
                            .title(title)
                            .company(company)
                            .location(jobLocation)
                            .jobUrl(jobUrl)
                            .postedText(postedText)
                            .postedAt(postedAt)
                            .tags(tags)
                            .description(description)
                            .build();

                    jobs.add(job);
                }
            }

            log.info("[RemoteOK] ✅ Parsed {} valid jobs from feed", jobs.size());
        } catch (Exception e) {
            log.error("[RemoteOK] ❌ Scrape failed: {}", e.getMessage());
        }

        return jobs;
    }
}
