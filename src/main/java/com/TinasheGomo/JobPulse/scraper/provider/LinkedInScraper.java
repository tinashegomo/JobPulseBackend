package com.TinasheGomo.JobPulse.scraper.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class LinkedInScraper {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    private static final Set<String> EXCLUDED_COMPANIES = Set.of(
            "hire feed", "hired", "quick hire staffing", "quik hire staffing", "crossing hurdles"
    );

    private static final Pattern RELATIVE_TIME_PATTERN =
            Pattern.compile("(\\d+)\\s*(minute|min|m\\b|hour|hr|h\\b|day|d\\b|week|w\\b|month|mo)");

    private static final Pattern CARD_PATTERN =
            Pattern.compile(
                    "<div[^>]*data-entity-urn=\"([^\"]+)\"[^>]*>.*?" +
                    "<h3[^>]*class=\"[^\"]*base-search-card__title[^\"]*\"[^>]*>(.*?)</h3>.*?" +
                    "<h4[^>]*class=\"[^\"]*base-search-card__subtitle[^\"]*\"[^>]*>(.*?)</h4>.*?" +
                    "<span[^>]*class=\"[^\"]*job-search-card__location[^\"]*\"[^>]*>(.*?)</span>.*?" +
                    "<a[^>]*class=\"[^\"]*base-card__full-link[^\"]*\"[^>]*href=\"([^\"]+)\"[^>]*>.*?" +
                    "<time(?:(?:[^>]*datetime=\"([^\"]*?)\")?)[^>]*>(.*?)</time>",
                    Pattern.DOTALL
            );

    private static final Pattern DESCRIPTION_PATTERN_1 =
            Pattern.compile("<div[^>]*class=\"[^\"]*show-more-less-html__markup[^\"]*\"[^>]*>(.*?)</div>",
                    Pattern.DOTALL);

    private static final Pattern DESCRIPTION_PATTERN_2 =
            Pattern.compile("<div[^>]*class=\"[^\"]*description__text[^\"]*\"[^>]*>(.*?)</div>",
                    Pattern.DOTALL);

    private static final Pattern DESCRIPTION_PATTERN_3 =
            Pattern.compile("<div[^>]*class=\"[^\"]*job-details-workflow__markdown[^\"]*\"[^>]*>(.*?)</div>",
                    Pattern.DOTALL);

    private final Map<String, String> searchCache = new ConcurrentHashMap<>();

    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 10_000;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(60))
            .build();

    private int rateLimitCount = 0;

    public List<ScrapedJob> scrape(String searchUrl) {
        log.info("[LinkedIn] Scraping URL: {}", searchUrl);
        List<ScrapedJob> jobs = new ArrayList<>();

        try {
            String html = fetchSearchPage(searchUrl);
            List<Map<String, String>> rawCards = parseJobCards(html);
            log.info("[LinkedIn] Found {} raw job cards in HTML", rawCards.size());

            int parsedCount = 0;
            int failedParse = 0;

            for (Map<String, String> card : rawCards) {
                String company = card.get("company");
                if (company != null && EXCLUDED_COMPANIES.contains(company.toLowerCase())) {
                    log.debug("[LinkedIn] Skipping excluded company: {}", company);
                    continue;
                }

                String postedText = card.get("postedText");
                String datetimeAttr = card.get("datetimeAttr");
                LocalDateTime postedAt = parseRelativeTime(postedText);

                // Fallback: try parsing the datetime attribute (ISO format)
                if (postedAt == null && datetimeAttr != null && !datetimeAttr.isEmpty()) {
                    postedAt = parseIsoDateTime(datetimeAttr);
                    if (postedAt != null) {
                        log.debug("[LinkedIn] Parsed datetime attr '{}' for '{}'", datetimeAttr, card.get("title"));
                    }
                }

                if (postedAt == null && postedText != null && !postedText.isEmpty()) {
                    log.debug("[LinkedIn] Could not parse postedText '{}' for '{}'", postedText, card.get("title"));
                    failedParse++;
                }
                if (postedAt != null) parsedCount++;

                String description = fetchJobDescription(card.get("jobUrl"));

                ScrapedJob job = ScrapedJob.builder()
                        .externalJobId(card.get("externalJobId"))
                        .title(card.get("title"))
                        .company(company)
                        .location(card.get("location"))
                        .jobUrl(card.get("jobUrl"))
                        .postedText(postedText)
                        .postedAt(postedAt)
                        .tags(new ArrayList<>())
                        .description(description)
                        .build();

                jobs.add(job);
            }

            log.info("[LinkedIn] Parsed {} jobs ({} with valid postedAt, {} with unparseable time)",
                    jobs.size(), parsedCount, failedParse);
        } catch (Exception e) {
            log.error("[LinkedIn] Scrape failed for {}: {}", searchUrl, e.getMessage());
        }

        return jobs;
    }

    private String fetchSearchPage(String url) throws IOException, InterruptedException {
        if (searchCache.containsKey(url)) {
            log.debug("[LinkedIn] Cache hit for {}", url);
            return searchCache.get(url);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en-US,en;q=0.9")
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        HttpResponse<String> response = null;
        IOException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                log.info("[LinkedIn] HTTP GET {} (attempt {}/{})", url, attempt, MAX_RETRIES);
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                log.info("[LinkedIn] HTTP {} ({} bytes)", response.statusCode(), response.body().length());

                if (response.statusCode() == 429) {
                    rateLimitCount++;
                    long backoffMs = 60_000 + (long) (Math.random() * 60_000);
                    log.warn("[LinkedIn] 429 rate-limited (attempt #{}). Waiting {}s...", rateLimitCount, backoffMs / 1000);
                    sleep(backoffMs);
                    continue;
                }

                if (response.statusCode() / 100 == 2) {
                    String html = response.body();
                    searchCache.put(url, html);
                    return html;
                }

                log.warn("[LinkedIn] HTTP {} on attempt {}", response.statusCode(), attempt);
                lastException = new IOException("Fetch failed with status " + response.statusCode());
            } catch (IOException e) {
                lastException = e;
                log.warn("[LinkedIn] Request failed (attempt {}/{}): {}", attempt, MAX_RETRIES, e.getMessage());
            }

            if (attempt < MAX_RETRIES) {
                long backoff = BASE_BACKOFF_MS * (1L << (attempt - 1));
                log.info("[LinkedIn] Retrying in {}s...", backoff / 1000);
                sleep(backoff);
            }
        }

        throw lastException != null ? lastException : new IOException("All " + MAX_RETRIES + " attempts failed");
    }

    private List<Map<String, String>> parseJobCards(String html) {
        List<Map<String, String>> jobs = new ArrayList<>();
        Matcher matcher = CARD_PATTERN.matcher(html);

        while (matcher.find()) {
            String urn = matcher.group(1).trim();
            String title = stripTags(matcher.group(2)).trim();
            String company = stripTags(matcher.group(3)).trim();
            String location = stripTags(matcher.group(4)).trim();
            String jobUrl = matcher.group(5).trim();
            String datetimeAttr = matcher.group(6) != null ? matcher.group(6).trim() : "";
            String postedText = stripTags(matcher.group(7)).trim();

            String externalJobId = "";
            if (!urn.isEmpty()) {
                String[] parts = urn.split(":");
                externalJobId = parts[parts.length - 1];
            }

            if (!externalJobId.isEmpty() && !title.isEmpty()) {
                Map<String, String> jobData = new HashMap<>();
                jobData.put("externalJobId", externalJobId);
                jobData.put("title", title);
                jobData.put("company", company);
                jobData.put("location", location);
                jobData.put("jobUrl", jobUrl);
                jobData.put("postedText", postedText);
                jobData.put("datetimeAttr", datetimeAttr);
                jobs.add(jobData);
            }
        }

        return jobs;
    }

    private String fetchJobDescription(String jobUrl) {
        if (jobUrl == null || jobUrl.isEmpty()) {
            return "";
        }

        try {
            String html = fetchSearchPage(jobUrl);

            Pattern[] selectors = {DESCRIPTION_PATTERN_1, DESCRIPTION_PATTERN_2, DESCRIPTION_PATTERN_3};
            for (Pattern pattern : selectors) {
                Matcher m = pattern.matcher(html);
                if (m.find()) {
                    return stripTags(m.group(1)).trim();
                }
            }

            log.debug("[LinkedIn] No description container found on {}", jobUrl);
            return "";
        } catch (Exception e) {
            log.warn("[LinkedIn] Failed to fetch job description: {}", e.getMessage());
            return "";
        }
    }

    private String stripTags(String html) {
        if (html == null) return "";
        String result = html;
        // Preserve structure before stripping
        result = result.replaceAll("<li[^>]*>", "\n• ");
        result = result.replaceAll("<br\\s*/?>", "\n");
        result = result.replaceAll("</?p[^>]*>", "\n");
        result = result.replaceAll("</?div[^>]*>", "\n");
        result = result.replaceAll("</?h[1-6][^>]*>", "\n");
        result = result.replaceAll("</?ul[^>]*>", "\n");
        result = result.replaceAll("</?ol[^>]*>", "\n");
        // Strip remaining tags
        result = result.replaceAll("<[^>]+>", "");
        // Decode HTML entities
        result = result.replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<").replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"").replaceAll("&#39;", "'")
                .replaceAll("&nbsp;", " ");
        // Clean up excessive newlines
        result = result.replaceAll("\\n{3,}", "\n\n");
        return result.trim();
    }

    private LocalDateTime parseRelativeTime(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        String normalized = text.toLowerCase().trim();

        if (normalized.contains("just now") || normalized.contains("moment")) {
            return LocalDateTime.now();
        }

        Matcher matcher = RELATIVE_TIME_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return null;
        }

        int value = Integer.parseInt(matcher.group(1));
        String unit = matcher.group(2);

        if (unit.startsWith("min") || unit.equals("m")) {
            return LocalDateTime.now().minusMinutes(value);
        } else if (unit.startsWith("hour") || unit.equals("hr") || unit.equals("h")) {
            return LocalDateTime.now().minusHours(value);
        } else if (unit.startsWith("day") || unit.equals("d")) {
            return LocalDateTime.now().minusDays(value);
        } else if (unit.startsWith("week") || unit.equals("w")) {
            return LocalDateTime.now().minusDays(value * 7L);
        } else if (unit.startsWith("month") || unit.equals("mo")) {
            return LocalDateTime.now().minusDays(value * 30L);
        }

        return null;
    }

    private LocalDateTime parseIsoDateTime(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try {
            return LocalDateTime.parse(iso.substring(0, 19));
        } catch (Exception e) {
            try {
                return java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
            } catch (Exception ex) {
                return null;
            }
        }
    }

    public int getRateLimitCount() {
        return rateLimitCount;
    }

    public void clearCache() {
        searchCache.clear();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during backoff", e);
        }
    }
}
