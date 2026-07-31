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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ApplyNowScraper {

    private static final String APPLY_NOW_URL = "https://applynow.co.zw/category/zimbabwe/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    private static final Pattern SLUG_PATTERN =
            Pattern.compile("/(\\d{4})/(\\d{2})/(\\d{2})/([^/]+)/?$");

    private static final Pattern RELATIVE_TIME_PATTERN =
            Pattern.compile("(\\d+)\\s*(minute|min|m\\b|hour|hr|h\\b|day|d\\b|week|w\\b|month|mo)");

    private static final Pattern POST_PATTERN =
            Pattern.compile("<article[^>]*class=\"[^\"]*elementor-post[^\"]*\"[^>]*>(.*?)</article>",
                    Pattern.DOTALL);

    private static final Pattern POST_TITLE_PATTERN =
            Pattern.compile("<h3[^>]*class=\"[^\"]*elementor-post__title[^\"]*\"[^>]*>\\s*<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>",
                    Pattern.DOTALL);

    private static final Pattern POST_DATE_PATTERN =
            Pattern.compile("<span[^>]*class=\"[^\"]*(?:elementor-post-date|elementor-post__meta-data)[^\"]*\"[^>]*>(.*?)</span>",
                    Pattern.DOTALL);

    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 10_000;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(60))
            .build();

    public List<ScrapedJob> scrape() {
        log.info("[ApplyNow] 🌐 Scraping Zimbabwe job listings from {}", APPLY_NOW_URL);
        List<ScrapedJob> jobs = new ArrayList<>();

        try {
            String html = fetchPage(APPLY_NOW_URL);
            log.info("[ApplyNow] Fetched HTML ({} bytes)", html.length());

            Matcher postMatcher = POST_PATTERN.matcher(html);
            int postCount = 0;

            while (postMatcher.find()) {
                postCount++;
                String postHtml = postMatcher.group(1);

                Matcher titleMatcher = POST_TITLE_PATTERN.matcher(postHtml);
                if (!titleMatcher.find()) {
                    log.debug("[ApplyNow] Post #{}: no title found, skipping", postCount);
                    continue;
                }

                String jobUrl = titleMatcher.group(1).trim();
                String title = stripTags(titleMatcher.group(2)).trim();

                if (title.isEmpty() || jobUrl.isEmpty()) {
                    log.debug("[ApplyNow] Post #{}: empty title or url, skipping", postCount);
                    continue;
                }

                String postedText = "";
                Matcher dateMatcher = POST_DATE_PATTERN.matcher(postHtml);
                if (dateMatcher.find()) {
                    postedText = stripTags(dateMatcher.group(1)).trim();
                }

                String externalJobId = extractExternalJobId(jobUrl);
                LocalDateTime postedAt = parseRelativeTime(postedText);

                ScrapedJob job = ScrapedJob.builder()
                        .externalJobId(externalJobId)
                        .title(title)
                        .company("")
                        .location("Zimbabwe")
                        .jobUrl(jobUrl)
                        .postedText(postedText)
                        .postedAt(postedAt)
                        .tags(new ArrayList<>())
                        .description("")
                        .build();

                log.debug("[ApplyNow] Found: '{}' at {} (posted: {})", title, jobUrl, postedText);
                jobs.add(job);
            }

            log.info("[ApplyNow] ✅ Parsed {} jobs from Zimbabwe listings ({} posts scanned)", jobs.size(), postCount);
        } catch (Exception e) {
            log.error("[ApplyNow] ❌ Scrape failed: {}", e.getMessage());
        }

        return jobs;
    }

    private String fetchPage(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en-US,en;q=0.9")
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        IOException lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                log.info("[ApplyNow] HTTP GET {} (attempt {}/{})", url, attempt, MAX_RETRIES);
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                log.info("[ApplyNow] HTTP {} ({} bytes)", response.statusCode(), response.body().length());

                if (response.statusCode() / 100 == 2) {
                    return response.body();
                }

                log.warn("[ApplyNow] HTTP {} on attempt {}", response.statusCode(), attempt);
                lastException = new IOException("Fetch failed with status " + response.statusCode());
            } catch (IOException e) {
                lastException = e;
                log.warn("[ApplyNow] Request failed (attempt {}/{}): {}", attempt, MAX_RETRIES, e.getMessage());
            }

            if (attempt < MAX_RETRIES) {
                long backoff = BASE_BACKOFF_MS * (1L << (attempt - 1));
                log.info("[ApplyNow] Retrying in {}s...", backoff / 1000);
                try { Thread.sleep(backoff); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted during backoff", ie);
                }
            }
        }

        throw lastException != null ? lastException : new IOException("All " + MAX_RETRIES + " attempts failed");
    }

    private String extractExternalJobId(String jobUrl) {
        Matcher matcher = SLUG_PATTERN.matcher(jobUrl);
        if (matcher.find()) {
            return matcher.group(4);
        }
        return jobUrl.replaceAll("\\W+", "_");
    }

    private String stripTags(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", "").replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<").replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"").replaceAll("&#39;", "'")
                .replaceAll("&nbsp;", " ").trim();
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
}
