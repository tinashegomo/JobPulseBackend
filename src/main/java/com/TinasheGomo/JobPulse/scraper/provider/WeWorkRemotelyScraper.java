package com.TinasheGomo.JobPulse.scraper.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
public class WeWorkRemotelyScraper implements JobScraper {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    private static final Pattern JOB_CARD_PATTERN =
            Pattern.compile(
                    "<li[^>]*class=\"[^\"]*feature[^\"]*\"[^>]*>.*?" +
                    "<a[^>]*href=\"(/remote-jobs/[^\"]+)\"[^>]*>.*?" +
                    "<span[^>]*class=\"[^\"]*company[^\"]*\"[^>]*>(.*?)</span>.*?" +
                    "<span[^>]*class=\"[^\"]*title[^\"]*\"[^>]*>(.*?)</span>.*?" +
                    "</a>",
                    Pattern.DOTALL
            );

    private static final Pattern JOB_CARD_PATTERN_2 =
            Pattern.compile(
                    "<a[^>]*href=\"(/remote-jobs/[^\"]+)\"[^>]*>.*?" +
                    "<h2[^>]*>(.*?)</h2>.*?" +
                    "<span[^>]*class=\"[^\"]*company-name[^\"]*\"[^>]*>(.*?)</span>",
                    Pattern.DOTALL
            );

    private static final Pattern DATE_PATTERN =
            Pattern.compile("<span[^>]*class=\"[^\"]*date[^\"]*\"[^>]*>(.*?)</span>",
                    Pattern.DOTALL);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public String getSource() {
        return "WEWORKREMOTELY";
    }

    @Override
    public List<ScrapedJob> scrape(String keywords, String location) {
        String searchUrl = buildSearchUrl(keywords, location);
        log.info("[WeWorkRemotely] 🌐 Scraping URL: {}", searchUrl);
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
            log.info("[WeWorkRemotely] HTTP {} ({} bytes)", response.statusCode(), response.body().length());

            if (response.statusCode() / 100 != 2) {
                log.warn("[WeWorkRemotely] Non-2xx status: {}", response.statusCode());
                return jobs;
            }

            String html = response.body();

            List<ScrapedJob> v1 = parseCards(html, JOB_CARD_PATTERN);
            List<ScrapedJob> v2 = parseCardsV2(html, JOB_CARD_PATTERN_2);
            jobs.addAll(v1);
            if (jobs.isEmpty()) jobs.addAll(v2);

            log.info("[WeWorkRemotely] ✅ Parsed {} jobs", jobs.size());
        } catch (Exception e) {
            log.error("[WeWorkRemotely] ❌ Scrape failed: {}", e.getMessage());
        }

        long elapsed = (System.currentTimeMillis() - start) / 1000;
        log.info("[WeWorkRemotely] Done — {} jobs ({}s)", jobs.size(), elapsed);
        return jobs;
    }

    private List<ScrapedJob> parseCards(String html, Pattern pattern) {
        List<ScrapedJob> jobs = new ArrayList<>();
        Matcher matcher = pattern.matcher(html);

        while (matcher.find()) {
            String path = matcher.group(1).trim();
            String jobUrl = "https://weworkremotely.com" + path;
            String company = stripTags(matcher.group(2)).trim();
            String title = stripTags(matcher.group(3)).trim();

            if (title.isEmpty()) continue;

            String externalJobId = path.replaceAll("[^a-zA-Z0-9]", "_");

            jobs.add(ScrapedJob.builder()
                    .externalJobId(externalJobId)
                    .title(title)
                    .company(company)
                    .location("Remote")
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
            String jobUrl = "https://weworkremotely.com" + path;
            String title = stripTags(matcher.group(2)).trim();
            String company = matcher.groupCount() >= 3 ? stripTags(matcher.group(3)).trim() : "";

            if (title.isEmpty()) continue;

            String externalJobId = path.replaceAll("[^a-zA-Z0-9]", "_");

            jobs.add(ScrapedJob.builder()
                    .externalJobId(externalJobId)
                    .title(title)
                    .company(company)
                    .location("Remote")
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
        if (keywords != null && !keywords.isBlank()) {
            return "https://weworkremotely.com/remote-jobs/search?term=" + keywords.replace(" ", "+");
        }
        return "https://weworkremotely.com/categories/remote-programming-jobs";
    }

    private String stripTags(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", "")
                .replaceAll("&amp;", "&").replaceAll("&lt;", "<").replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"").replaceAll("&#39;", "'").replaceAll("&nbsp;", " ")
                .trim();
    }
}
