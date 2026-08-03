package com.TinasheGomo.JobPulse.scraper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class DescriptionFetcher {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    private static final int CONCURRENCY = 8;
    private static final int MAX_RETRIES = 2;
    private static final long BASE_BACKOFF_MS = 2000;

    private static final Pattern DESCRIPTION_PATTERN_1 =
            Pattern.compile("<div[^>]*class=\"[^\"]*show-more-less-html__markup[^\"]*\"[^>]*>(.*?)</div>",
                    Pattern.DOTALL);
    private static final Pattern DESCRIPTION_PATTERN_2 =
            Pattern.compile("<div[^>]*class=\"[^\"]*description__text[^\"]*\"[^>]*>(.*?)</div>",
                    Pattern.DOTALL);
    private static final Pattern DESCRIPTION_PATTERN_3 =
            Pattern.compile("<div[^>]*class=\"[^\"]*job-details-workflow__markdown[^\"]*\"[^>]*>(.*?)</div>",
                    Pattern.DOTALL);
    private static final Pattern DESCRIPTION_PATTERN_4 =
            Pattern.compile("<div[^>]*class=\"[^\"]*job-description[^\"]*\"[^>]*>(.*?)</div>",
                    Pattern.DOTALL);
    private static final Pattern DESCRIPTION_PATTERN_5 =
            Pattern.compile("<section[^>]*class=\"[^\"]*job-description[^\"]*\"[^>]*>(.*?)</section>",
                    Pattern.DOTALL);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * Fetch descriptions for multiple URLs in parallel.
     * Returns a Map of URL -> description text.
     */
    public Map<String, String> fetchDescriptionsParallel(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return Map.of();
        }

        log.info("[DescriptionFetcher] 🚀 Fetching {} descriptions with concurrency {}", urls.size(), CONCURRENCY);
        long start = System.currentTimeMillis();

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        Map<String, String> results = new ConcurrentHashMap<>();

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        List<Future<?>> futures = new ArrayList<>();

        for (String url : urls) {
            futures.add(executor.submit(() -> {
                try {
                    String description = fetchWithRetry(url);
                    if (description != null && !description.isBlank()) {
                        results.put(url, description);
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    log.debug("[DescriptionFetcher] Failed to fetch {}: {}", url, e.getMessage());
                }
            }));
        }

        // Wait for all to complete
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.debug("[DescriptionFetcher] Future timeout: {}", e.getMessage());
            }
        }

        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long elapsed = (System.currentTimeMillis() - start) / 1000;
        log.info("[DescriptionFetcher] ✅ Done — {}/{} succeeded, {} failed ({}s)",
                successCount.get(), urls.size(), failCount.get(), elapsed);

        return results;
    }

    /**
     * Fetch a single URL's description with retry logic.
     */
    public String fetchDescription(String url) {
        String result = fetchWithRetry(url);
        return result != null ? result : "";
    }

    private String fetchWithRetry(String url) {
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .timeout(Duration.ofSeconds(15))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() / 100 == 2) {
                    return extractDescription(response.body());
                }

                if (response.statusCode() == 429) {
                    long backoff = BASE_BACKOFF_MS * (1L << (attempt - 1)) + (long)(Math.random() * 2000);
                    log.debug("[DescriptionFetcher] 429 on {}, waiting {}ms", url, backoff);
                    sleep(backoff);
                    continue;
                }

                log.debug("[DescriptionFetcher] HTTP {} for {}", response.statusCode(), url);
                return null;

            } catch (Exception e) {
                if (attempt < MAX_RETRIES) {
                    long backoff = BASE_BACKOFF_MS * (1L << (attempt - 1));
                    sleep(backoff);
                }
            }
        }
        return null;
    }

    private String extractDescription(String html) {
        Pattern[] patterns = {DESCRIPTION_PATTERN_1, DESCRIPTION_PATTERN_2, DESCRIPTION_PATTERN_3,
                DESCRIPTION_PATTERN_4, DESCRIPTION_PATTERN_5};

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(html);
            if (matcher.find()) {
                return stripTags(matcher.group(1)).trim();
            }
        }

        // Fallback: find largest text block that looks like a job description
        return extractLargestTextBlock(html);
    }

    private String extractLargestTextBlock(String html) {
        // Find all <p> blocks and pick the longest one
        Pattern pTag = Pattern.compile("<p[^>]*>(.*?)</p>", Pattern.DOTALL);
        Matcher matcher = pTag.matcher(html);

        String longest = "";
        while (matcher.find()) {
            String text = stripTags(matcher.group(1)).trim();
            if (text.length() > longest.length()) {
                longest = text;
            }
        }

        return longest.length() > 50 ? longest : "";
    }

    private String stripTags(String html) {
        if (html == null) return "";
        String result = html;
        result = result.replaceAll("<li[^>]*>", "\n• ");
        result = result.replaceAll("<br\\s*/?>", "\n");
        result = result.replaceAll("</?p[^>]*>", "\n");
        result = result.replaceAll("</?div[^>]*>", "\n");
        result = result.replaceAll("</?h[1-6][^>]*>", "\n");
        result = result.replaceAll("</?ul[^>]*>", "\n");
        result = result.replaceAll("</?ol[^>]*>", "\n");
        result = result.replaceAll("<[^>]+>", "");
        result = result.replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<").replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"").replaceAll("&#39;", "'")
                .replaceAll("&nbsp;", " ");
        result = result.replaceAll("\\n{3,}", "\n\n");
        return result.trim();
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
