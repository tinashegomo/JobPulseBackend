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
public class IndeedScraper implements JobScraper {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    private static final Pattern JOB_CARD_PATTERN =
            Pattern.compile(
                    "<div[^>]*class=\"[^\"]*job_seen_beacon[^\"]*\"[^>]*>.*?" +
                    "<h2[^>]*class=\"[^\"]*jobTitle[^\"]*\"[^>]*>.*?<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>.*?" +
                    "</h2>.*?" +
                    "<span[^>]*data-testid=\"company-name\"[^>]*>(.*?)</span>.*?" +
                    "<div[^>]*data-testid=\"text-location\"[^>]*>(.*?)</div>",
                    Pattern.DOTALL
            );

    private static final Pattern JOB_CARD_PATTERN_2 =
            Pattern.compile(
                    "<td[^>]*class=\"[^\"]*resultContent[^\"]*\"[^>]*>.*?" +
                    "<h2[^>]*class=\"[^\"]*jobTitle[^\"]*\"[^>]*>.*?<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>.*?" +
                    "</h2>.*?" +
                    "<span[^>]*class=\"[^\"]*companyName[^\"]*\"[^>]*>(.*?)</span>.*?" +
                    "<span[^>]*class=\"[^\"]*companyLocation[^\"]*\"[^>]*>(.*?)</span>",
                    Pattern.DOTALL
            );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public String getSource() {
        return "INDEED";
    }

    @Override
    public List<ScrapedJob> scrape(String keywords, String location) {
        String searchUrl = buildSearchUrl(keywords, location);
        log.info("[Indeed] 🌐 Scraping URL: {}", searchUrl);
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
            log.info("[Indeed] HTTP {} ({} bytes)", response.statusCode(), response.body().length());

            if (response.statusCode() / 100 != 2) {
                log.warn("[Indeed] Non-2xx status: {}", response.statusCode());
                return jobs;
            }

            String html = response.body();

            List<ScrapedJob> jobsV1 = parseCards(html, JOB_CARD_PATTERN);
            List<ScrapedJob> jobsV2 = parseCards(html, JOB_CARD_PATTERN_2);

            jobs.addAll(jobsV1);
            if (jobs.isEmpty()) {
                jobs.addAll(jobsV2);
            }

            log.info("[Indeed] ✅ Parsed {} jobs", jobs.size());
        } catch (Exception e) {
            log.error("[Indeed] ❌ Scrape failed: {}", e.getMessage());
        }

        long elapsed = (System.currentTimeMillis() - start) / 1000;
        log.info("[Indeed] Done — {} jobs ({}s)", jobs.size(), elapsed);
        return jobs;
    }

    private List<ScrapedJob> parseCards(String html, Pattern pattern) {
        List<ScrapedJob> jobs = new ArrayList<>();
        Matcher matcher = pattern.matcher(html);

        while (matcher.find()) {
            String jobUrl = "https://www.indeed.com" + matcher.group(1).trim();
            String title = stripTags(matcher.group(2)).trim();
            String company = stripTags(matcher.group(3)).trim();
            String loc = stripTags(matcher.group(4)).trim();

            if (title.isEmpty()) continue;

            String externalJobId = extractIdFromUrl(jobUrl);

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

    private String buildSearchUrl(String keywords, String location) {
        StringBuilder url = new StringBuilder("https://www.indeed.com/jobs?");
        if (keywords != null && !keywords.isBlank()) {
            url.append("q=").append(keywords.replace(" ", "+"));
        }
        if (location != null && !location.isBlank()) {
            url.append("&l=").append(location.replace(" ", "+"));
        }
        return url.toString();
    }

    private String extractIdFromUrl(String url) {
        Matcher m = Pattern.compile("jk=([a-f0-9]+)").matcher(url);
        if (m.find()) return m.group(1);
        return url.replaceAll("[^a-zA-Z0-9]", "_").substring(0, Math.min(64, url.length()));
    }

    private String stripTags(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", "")
                .replaceAll("&amp;", "&").replaceAll("&lt;", "<").replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"").replaceAll("&#39;", "'").replaceAll("&nbsp;", " ")
                .trim();
    }
}
