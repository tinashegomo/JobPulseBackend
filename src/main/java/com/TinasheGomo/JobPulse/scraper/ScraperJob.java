package com.TinasheGomo.JobPulse.scraper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class ScraperJob {
    private final ScraperOrchestrator orchestrator;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public void execute() {
        if (!running.compareAndSet(false, true)) {
            log.warn("⚠ Scraper already running — skipping this cycle");
            return;
        }
        long start = System.currentTimeMillis();
        try {
            log.info("═══════════════════════════════════════════════════");
            log.info("🚀 SCRAPER CYCLE STARTED at {}", java.time.LocalTime.now());
            log.info("═══════════════════════════════════════════════════");
            orchestrator.run();
            long duration = (System.currentTimeMillis() - start) / 1000;
            log.info("═══════════════════════════════════════════════════");
            log.info("✅ SCRAPER CYCLE COMPLETE — took {}s", duration);
            log.info("═══════════════════════════════════════════════════");
        } catch (Exception e) {
            long duration = (System.currentTimeMillis() - start) / 1000;
            log.error("❌ SCRAPER CYCLE FAILED after {}s: {}", duration, e.getMessage(), e);
        } finally {
            running.set(false);
        }
    }
}
