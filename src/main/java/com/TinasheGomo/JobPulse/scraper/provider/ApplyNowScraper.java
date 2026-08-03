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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ApplyNowScraper implements JobScraper {

    private static final String APPLY_NOW_URL = "https://applynow.co.zw/category/zimbabwe/";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    private static final Pattern POST_PATTERN =
            Pattern.compile("<div[^>]*class=\"[^\"]*p-wrap[^\"]*\"[^>]*data-pid=\"(\\d+)\"[^>]*>(.*?)</div>\\s*<div[^>]*class=\"[^\"]*p-wrap",
                    Pattern.DOTALL);

    private static final Pattern POST_TITLE_PATTERN =
            Pattern.compile("<h2[^>]*class=\"[^\"]*entry-title[^\"]*\"[^>]*>\\s*<a[^>]*class=\"[^\"]*p-url[^\"]*\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>",
                    Pattern.DOTALL);

    private static final Pattern POST_DATE_PATTERN =
            Pattern.compile("<time[^>]*class=\"[^\"]*updated[^\"]*\"[^>]*datetime=\"([^\"]+)\"[^>]*>(.*?)</time>",
                    Pattern.DOTALL);

    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 10_000;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(60))
            .build();

    @Override
    public String getSource() {
        return "APPLYNOW";
    }

    @Override
    public Set<String> supportedLocations() {
        return Set.of("Zimbabwe", "ZW");
    }

    @Override
    public List<ScrapedJob> scrape(String keywords, String location) {
        log.info("[ApplyNow] Scraping Zimbabwe job listings from {}", APPLY_NOW_URL);
        List<ScrapedJob> jobs = new ArrayList<>();

        try {
            String html = fetchPage(APPLY_NOW_URL);
            log.info("[ApplyNow] Fetched HTML ({} bytes)", html.length());

            // Split into post blocks by finding each p-wrap container
            String[] blocks = html.split("<div[^>]*class=\"[^\"]*p-wrap[^\"]*\"[^>]*data-pid=\"");
            log.info("[ApplyNow] Found {} post blocks", blocks.length - 1);

            for (int i = 1; i < blocks.length; i++) {
                String block = blocks[i];

                // Extract title and URL
                Matcher titleMatcher = POST_TITLE_PATTERN.matcher(block);
                if (!titleMatcher.find()) {
                    log.debug("[ApplyNow] Block #{}: no title found, skipping", i);
                    continue;
                }

                String jobUrl = titleMatcher.group(1).trim();
                String title = stripTags(titleMatcher.group(2)).trim();

                if (title.isEmpty() || jobUrl.isEmpty()) {
                    log.debug("[ApplyNow] Block #{}: empty title or url, skipping", i);
                    continue;
                }

                // Extract date
                String postedText = "";
                LocalDateTime postedAt = null;
                Matcher dateMatcher = POST_DATE_PATTERN.matcher(block);
                if (dateMatcher.find()) {
                    String isoDatetime = dateMatcher.group(1).trim();
                    postedText = stripTags(dateMatcher.group(2)).trim();
                    postedAt = parseIsoDatetime(isoDatetime);
                }

                // Extract external ID from URL slug
                String externalJobId = extractExternalJobId(jobUrl);

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

            log.info("[ApplyNow] Parsed {} jobs from Zimbabwe listings", jobs.size());
        } catch (Exception e) {
            log.error("[ApplyNow] Scrape failed: {}", e.getMessage());
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
        // Extract slug from URL like /2026/08/03/empowerbank-11/
        Matcher matcher = Pattern.compile("/(\\d{4})/(\\d{2})/(\\d{2})/([^/]+)/?$").matcher(jobUrl);
        if (matcher.find()) {
            return matcher.group(4);
        }
        return jobUrl.replaceAll("\\W+", "_");
    }

    private LocalDateTime parseIsoDatetime(String isoDatetime) {
        if (isoDatetime == null || isoDatetime.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(isoDatetime)
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (Exception e) {
            log.debug("[ApplyNow] Failed to parse datetime '{}': {}", isoDatetime, e.getMessage());
            return null;
        }
    }

    private String stripTags(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", "").replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<").replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"").replaceAll("&#39;", "'")
                .replaceAll("&nbsp;", " ").trim();
    }
}
