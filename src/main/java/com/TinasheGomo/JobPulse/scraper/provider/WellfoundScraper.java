package com.TinasheGomo.JobPulse.scraper.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class WellfoundScraper implements JobScraper {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    private static final Pattern JOB_CARD_PATTERN =
            Pattern.compile(
                    "<div[^>]*class=\"[^\"]*styles_jobCardWrapper[^\"]*\"[^>]*>.*?" +
                    "<a[^>]*href=\"(/job[^\"]+)\"[^>]*>.*?" +
                    "<h2[^>]*>(.*?)</h2>.*?" +
                    "</a>.*?" +
                    "<span[^>]*class=\"[^\"]*styles_companyName[^\"]*\"[^>]*>(.*?)</span>.*?" +
                    "<span[^>]*class=\"[^\"]*styles_location[^\"]*\"[^>]*>(.*?)</span>",
                    Pattern.DOTALL
            );

    private static final Pattern JOB_CARD_PATTERN_2 =
            Pattern.compile(
                    "<a[^>]*href=\"(/job[^\"]+)\"[^>]*class=\"[^\"]*job-link[^\"]*\"[^>]*>.*?" +
                    "<span[^>]*>(.*?)</span>.*?" +
                    "</a>.*?" +
                    "<a[^>]*class=\"[^\"]*company-link[^\"]*\"[^>]*>(.*?)</a>",
                    Pattern.DOTALL
            );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public String getSource() {
        return "WELLFOUND";
    }

    @Override
    public List<ScrapedJob> scrape(String keywords, String location) {
        String searchUrl = buildSearchUrl(keywords, location);
        log.info("[Wellfound] 🌐 Scraping URL: {}", searchUrl);
        long start = System.currentTimeMillis();
        List<ScrapedJob> jobs = new ArrayList<>();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("[Wellfound] HTTP {} ({} bytes)", response.statusCode(), response.body().length());

            if (response.statusCode() / 100 != 2) {
                log.warn("[Wellfound] Non-2xx status: {}", response.statusCode());
                return jobs;
            }

            String html = response.body();

            List<ScrapedJob> v1 = parseCards(html, JOB_CARD_PATTERN);
            List<ScrapedJob> v2 = parseCardsV2(html, JOB_CARD_PATTERN_2);
            jobs.addAll(v1);
            if (jobs.isEmpty()) jobs.addAll(v2);

            log.info("[Wellfound] ✅ Parsed {} jobs", jobs.size());
        } catch (Exception e) {
            log.error("[Wellfound] ❌ Scrape failed: {}", e.getMessage());
        }

        long elapsed = (System.currentTimeMillis() - start) / 1000;
        log.info("[Wellfound] Done — {} jobs ({}s)", jobs.size(), elapsed);
        return jobs;
    }

    private List<ScrapedJob> parseCards(String html, Pattern pattern) {
        List<ScrapedJob> jobs = new ArrayList<>();
        Matcher matcher = pattern.matcher(html);

        while (matcher.find()) {
            String path = matcher.group(1).trim();
            String jobUrl = "https://wellfound.com" + path;
            String title = stripTags(matcher.group(2)).trim();
            String company = matcher.groupCount() >= 3 ? stripTags(matcher.group(3)).trim() : "";
            String loc = matcher.groupCount() >= 4 ? stripTags(matcher.group(4)).trim() : "";

            if (title.isEmpty()) continue;

            String externalJobId = path.replaceAll("[^a-zA-Z0-9]", "_");

            jobs.add(ScrapedJob.builder()
                    .externalJobId(externalJobId)
                    .title(title)
                    .company(company)
                    .location(loc)
                    .jobUrl(jobUrl)
                    .postedText("")
                    .postedAt(null)
                    .tags(new ArrayList<>())
                    .description("")
                    .build());
        }
        return jobs;
    }

    private List<ScrapedJob> parseCardsV2(String html, Pattern pattern) {
        List<ScrapedJob> jobs = new ArrayList<>();
        Matcher matcher = pattern.matcher(html);

        while (matcher.find()) {
            String path = matcher.group(1).trim();
            String jobUrl = "https://wellfound.com" + path;
            String title = stripTags(matcher.group(2)).trim();
            String company = matcher.groupCount() >= 3 ? stripTags(matcher.group(3)).trim() : "";

            if (title.isEmpty()) continue;

            String externalJobId = path.replaceAll("[^a-zA-Z0-9]", "_");

            jobs.add(ScrapedJob.builder()
                    .externalJobId(externalJobId)
                    .title(title)
                    .company(company)
                    .location("")
                    .jobUrl(jobUrl)
                    .postedText("")
                    .postedAt(null)
                    .tags(new ArrayList<>())
                    .description("")
                    .build());
        }
        return jobs;
    }

    private String buildSearchUrl(String keywords, String location) {
        StringBuilder url = new StringBuilder("https://wellfound.com/jobs?");
        if (keywords != null && !keywords.isBlank()) {
            url.append("role=").append(keywords.replace(" ", "+"));
        }
        if (location != null && !location.isBlank()) {
            url.append("&location=").append(location.replace(" ", "+"));
        }
        return url.toString();
    }

    private String stripTags(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", "")
                .replaceAll("&amp;", "&").replaceAll("&lt;", "<").replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"").replaceAll("&#39;", "'").replaceAll("&nbsp;", " ")
                .trim();
    }
}
