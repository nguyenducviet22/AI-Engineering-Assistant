package com.aiassistant.ai;

import com.aiassistant.exception.ApiException;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;

public final class AiProviderFailureTranslator {
    public static final String SAFE_ERROR = "AI Provider Unavailable";
    public static final String SAFE_MESSAGE = "The assistant is temporarily unavailable, please try again.";

    private AiProviderFailureTranslator() {
    }

    public static ApiException unavailable(String component, Throwable ex, Logger log) {
        ProviderFailureCategory category = categorize(ex);
        log.warn("AI provider request failed: component={}, category={}", component, category, ex);
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, SAFE_ERROR, SAFE_MESSAGE);
    }

    private static ProviderFailureCategory categorize(Throwable ex) {
        String message = flattenMessages(ex).toLowerCase();
        if (message.contains("401") || message.contains("403") || message.contains("unauthorized")
                || message.contains("forbidden") || message.contains("invalid api key") || message.contains("authentication")) {
            return ProviderFailureCategory.AUTHENTICATION;
        }
        if (message.contains("429") || message.contains("rate limit") || message.contains("too many requests")) {
            return ProviderFailureCategory.RATE_LIMIT;
        }
        if (message.contains("timeout") || message.contains("timed out") || message.contains("read timed")) {
            return ProviderFailureCategory.TIMEOUT;
        }
        if (message.contains("402") || message.contains("insufficient credit") || message.contains("insufficient balance")
                || message.contains("credits")) {
            return ProviderFailureCategory.INSUFFICIENT_CREDITS;
        }
        return ProviderFailureCategory.PROVIDER_ERROR;
    }

    private static String flattenMessages(Throwable ex) {
        StringBuilder builder = new StringBuilder();
        Throwable current = ex;
        while (current != null) {
            if (current.getMessage() != null) {
                builder.append(current.getMessage()).append(' ');
            }
            current = current.getCause();
        }
        return builder.toString();
    }

    private enum ProviderFailureCategory {
        AUTHENTICATION,
        RATE_LIMIT,
        TIMEOUT,
        INSUFFICIENT_CREDITS,
        PROVIDER_ERROR
    }
}
