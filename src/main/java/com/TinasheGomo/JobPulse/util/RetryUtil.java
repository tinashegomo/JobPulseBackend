package com.TinasheGomo.JobPulse.util;

import java.util.Set;

public final class RetryUtil {

    private RetryUtil() {}

    private static final Set<Integer> RETRYABLE_STATUSES = Set.of(429, 500, 502, 503, 504);
    private static final int MAX_RETRIES = 3;
    private static final long BACKOFF_BASE_MS = 1000;

    public static boolean isRetryable(int status) {
        return RETRYABLE_STATUSES.contains(status);
    }

    @FunctionalInterface
    public interface RetryableAction<T> {
        T execute() throws Exception;
    }

    public static <T> T withRetry(RetryableAction<T> action, String label) throws Exception {
        Exception lastError = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                return action.execute();
            } catch (Exception e) {
                lastError = e;

                int status = extractStatus(e);
                if (!isRetryable(status) || attempt == MAX_RETRIES) {
                    throw e;
                }

                long delay = BACKOFF_BASE_MS * (1L << (attempt - 1));
                System.out.printf("[%s] Attempt %d failed (status %d), retrying in %dms...%n",
                        label, attempt, status, delay);
                Thread.sleep(delay);
            }
        }

        throw lastError;
    }

    private static int extractStatus(Exception e) {
        if (e instanceof org.springframework.web.client.HttpClientErrorException ex) {
            return ex.getStatusCode().value();
        }
        if (e instanceof org.springframework.web.client.HttpServerErrorException ex) {
            return ex.getStatusCode().value();
        }
        return 0;
    }
}
