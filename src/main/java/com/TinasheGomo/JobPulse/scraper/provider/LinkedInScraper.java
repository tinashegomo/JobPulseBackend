package com.TinasheGomo.JobPulse.scraper.provider;

import com.TinasheGomo.JobPulse.scraper.DescriptionFetcher;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class LinkedInScraper implements JobScraper {

    private final DescriptionFetcher descriptionFetcher;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    private static final Set<String> EXCLUDED_COMPANIES = Set.of(
            "hire feed", "hired", "quick hire staffing", "quik hire staffing", "crossing hurdles"
    );

    private static final Pattern RELATIVE_TIME_PATTERN =
            Pattern.compile("(\\d+)\\s*(minute|min|m\\b|hour|hr|h\\b|day|d\\b|week|w\\b|month|mo|year|yr|y\\b)");

    // Split card pattern — finds each card's data-entity-urn boundary
    private static final Pattern CARD_URN_PATTERN =
            Pattern.compile("data-entity-urn=\"urn:li:jobPosting:(\\d+)\"");

    // Individual field patterns (applied to each card block)
    private static final Pattern TITLE_PATTERN =
            Pattern.compile("<h3[^>]*class=\"[^\"]*base-search-card__title[^\"]*\"[^>]*>\\s*(.*?)\\s*</h3>", Pattern.DOTALL);

    private static final Pattern COMPANY_PATTERN =
            Pattern.compile("<h4[^>]*class=\"[^\"]*base-search-card__subtitle[^\"]*\"[^>]*>.*?<a[^>]*>(.*?)</a>", Pattern.DOTALL);

    private static final Pattern LOCATION_PATTERN =
            Pattern.compile("<span[^>]*class=\"[^\"]*job-search-card__location[^\"]*\"[^>]*>\\s*(.*?)\\s*</span>", Pattern.DOTALL);

    private static final Pattern URL_PATTERN =
            Pattern.compile("<a[^>]*class=\"[^\"]*base-card__full-link[^\"]*\"[^>]*href=\"([^\"]+)\"");

    private static final Pattern DATETIME_PATTERN =
            Pattern.compile("<time[^>]*datetime=\"([^\"]+)\"");

    private static final Pattern POSTED_TEXT_PATTERN =
            Pattern.compile("<time[^>]*>\\s*(.*?)\\s*</time>", Pattern.DOTALL);

    private final Map<String, String> searchCache = new ConcurrentHashMap<>();

    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 10_000;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(60))
            .build();

    private int rateLimitCount = 0;

    @Override
    public String getSource() {
        return "LINKEDIN";
    }

    @Override
    public List<ScrapedJob> scrape(String keywords, String location) {
        String searchUrl = buildSearchUrl(keywords, location);
        log.info("[LinkedIn] 🌐 Scraping URL: {}", searchUrl);
        long start = System.currentTimeMillis();
        List<ScrapedJob> jobs = new ArrayList<>();
        int pagesVisited = 0;

        try {
            String html = fetchSearchPage(searchUrl);
            pagesVisited++;
            List<Map<String, String>> rawCards = parseJobCards(html);
            log.info("[LinkedIn] Found {} raw job cards in HTML", rawCards.size());

            // Phase 1: Build job objects without descriptions
            List<String> jobUrls = new ArrayList<>();
            for (Map<String, String> card : rawCards) {
                String company = card.get("company");
                if (company != null && EXCLUDED_COMPANIES.contains(company.toLowerCase())) {
                    log.debug("[LinkedIn] Skipping excluded company: {}", company);
                    continue;
                }

                String postedText = card.get("postedText");
                String datetimeAttr = card.get("datetimeAttr");
                LocalDateTime postedAt = parseRelativeTime(postedText);

                if (postedAt == null && datetimeAttr != null && !datetimeAttr.isEmpty()) {
                    postedAt = parseIsoDateTime(datetimeAttr);
                }

                // Early rejection: skip jobs older than 15 hours
                if (postedAt != null) {
                    long hours = java.time.Duration.between(postedAt, LocalDateTime.now()).toHours();
                    if (hours > 15) {
                        log.debug("[LinkedIn] Skipping old job ({}h ago): {}", hours, card.get("title"));
                        continue;
                    }
                }

                String jobUrl = card.get("jobUrl");
                if (jobUrl != null && !jobUrl.isEmpty()) {
                    jobUrls.add(jobUrl);
                }

                ScrapedJob job = ScrapedJob.builder()
                        .externalJobId(card.get("externalJobId"))
                        .title(card.get("title"))
                        .company(company)
                        .location(card.get("location"))
                        .jobUrl(jobUrl)
                        .postedText(postedText)
                        .postedAt(postedAt)
                        .tags(new ArrayList<>())
                        .description("")
                        .build();

                jobs.add(job);
            }

            log.info("[LinkedIn] Parsed {} jobs, now fetching descriptions in parallel...", jobs.size());

            // Phase 2: Fetch all descriptions in parallel
            if (!jobUrls.isEmpty()) {
                Map<String, String> descriptions = descriptionFetcher.fetchDescriptionsParallel(jobUrls);

                // Phase 3: Assign descriptions back to jobs
                int descCount = 0;
                for (ScrapedJob job : jobs) {
                    String desc = descriptions.get(job.getJobUrl());
                    if (desc != null && !desc.isBlank()) {
                        job.setDescription(desc);
                        descCount++;
                    }
                }
                log.info("[LinkedIn] ✅ Got descriptions for {}/{} jobs", descCount, jobs.size());
            }

        } catch (Exception e) {
            log.error("[LinkedIn] Scrape failed for {}: {}", searchUrl, e.getMessage());
        }

        long elapsed = (System.currentTimeMillis() - start) / 1000;
        log.info("[LinkedIn] ✅ Done — {} jobs found, {} pages visited ({}s elapsed)",
                jobs.size(), pagesVisited, elapsed);
        return jobs;
    }

    private String buildSearchUrl(String keywords, String location) {
        StringBuilder url = new StringBuilder("https://www.linkedin.com/jobs/search/?");
        if (keywords != null && !keywords.isBlank()) {
            url.append("keywords=").append(keywords.replace(" ", "%20"));
        }
        if (location != null && !location.isBlank()) {
            url.append("&location=").append(location.replace(" ", "%20"));
        }
        return url.toString();
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

        // Step 1: Find all card boundaries using data-entity-urn
        List<int[]> cardBoundaries = new ArrayList<>();
        Matcher urnMatcher = CARD_URN_PATTERN.matcher(html);
        while (urnMatcher.find()) {
            cardBoundaries.add(new int[]{urnMatcher.start(), urnMatcher.end()});
        }

        // Step 2: Extract each card's HTML block (from one URN to the next)
        for (int i = 0; i < cardBoundaries.size(); i++) {
            int start = Math.max(0, cardBoundaries.get(i)[0] - 500); // Go back 500 chars to catch the opening div
            int end = (i + 1 < cardBoundaries.size())
                    ? cardBoundaries.get(i + 1)[0]
                    : Math.min(html.length(), cardBoundaries.get(i)[0] + 3000); // Card is usually < 3KB
            String cardHtml = html.substring(start, end);

            String externalJobId = extractField(CARD_URN_PATTERN, cardHtml);
            String title = stripTags(extractField(TITLE_PATTERN, cardHtml)).trim();
            String company = stripTags(extractField(COMPANY_PATTERN, cardHtml)).trim();
            String location = stripTags(extractField(LOCATION_PATTERN, cardHtml)).trim();
            String jobUrl = extractField(URL_PATTERN, cardHtml);
            String datetimeAttr = extractField(DATETIME_PATTERN, cardHtml);
            String postedText = stripTags(extractField(POSTED_TEXT_PATTERN, cardHtml)).trim();

            if (externalJobId != null && !externalJobId.isEmpty() && !title.isEmpty()) {
                Map<String, String> jobData = new HashMap<>();
                jobData.put("externalJobId", externalJobId);
                jobData.put("title", title);
                jobData.put("company", company);
                jobData.put("location", location);
                jobData.put("jobUrl", jobUrl);
                jobData.put("postedText", postedText);
                jobData.put("datetimeAttr", datetimeAttr != null ? datetimeAttr : "");
                jobs.add(jobData);
            }
        }

        return jobs;
    }

    private String extractField(Pattern pattern, String html) {
        Matcher m = pattern.matcher(html);
        return m.find() ? m.group(1) : null;
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
        } else if (unit.startsWith("year") || unit.equals("yr") || unit.equals("y")) {
            return LocalDateTime.now().minusDays(value * 365L);
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
