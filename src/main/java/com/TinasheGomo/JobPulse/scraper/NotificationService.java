package com.TinasheGomo.JobPulse.scraper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
@Slf4j
public class NotificationService {

    private final Map<String, List<String>> fcmTokenCache = new ConcurrentHashMap<>();

    public void notifyUser(String userId, String title, String body, String url) {
        List<String> tokens = getFcmTokens(userId);
        if (tokens.isEmpty()) {
            log.debug("No FCM tokens for user {}", userId);
            return;
        }

        // TODO: Re-enable when Firebase Admin dependency is available
        // For now, log the notification
        log.info("[FCM] Would send to user {}: '{}' | {} | url={} | tokens={}",
                userId, title, body, url, tokens.size());
    }

    public List<String> getFcmTokens(String userId) {
        return fcmTokenCache.getOrDefault(userId, List.of());
    }

    public void setFcmTokens(String userId, List<String> tokens) {
        fcmTokenCache.put(userId, tokens);
    }
}
