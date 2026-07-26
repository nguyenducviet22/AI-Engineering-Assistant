package com.aiassistant.ai.workflow;

import com.aiassistant.conversation.service.ConversationRetrievalContext;
import com.aiassistant.retrieval.Citation;
import com.aiassistant.retrieval.RetrievalResult;
import com.aiassistant.retrieval.RetrievedChunk;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public class QueryCondensationService {
    static final double MIN_RAW_FOLLOW_UP_TOP_SCORE = 0.20;
    private static final List<String> SHORT_FOLLOW_UPS = List.of(
            "why",
            "why?",
            "go on",
            "and then",
            "and then?",
            "then",
            "then?",
            "continue"
    );
    private static final List<String> FOLLOW_UP_MARKERS = List.of(
            "that",
            "this",
            "it",
            "those",
            "these",
            "more detail",
            "tell me more",
            "explain more",
            "go on",
            "and then",
            "what about",
            "continue"
    );

    public boolean shouldAttemptCondensation(String currentMessage,
                                             RetrievalResult rawResult,
                                             Optional<ConversationRetrievalContext> priorSuccessfulTurn) {
        return priorSuccessfulTurn != null
                && priorSuccessfulTurn.isPresent()
                && isFollowUpLike(currentMessage)
                && topScore(rawResult) >= MIN_RAW_FOLLOW_UP_TOP_SCORE;
    }

    public String condense(String currentMessage, Optional<ConversationRetrievalContext> priorSuccessfulTurn) {
        String message = currentMessage == null ? "" : currentMessage.trim();
        if (priorSuccessfulTurn == null || priorSuccessfulTurn.isEmpty()) {
            return message;
        }
        ConversationRetrievalContext prior = priorSuccessfulTurn.get();
        return """
                Previous successful user question:
                %s

                Previous successful assistant answer:
                %s

                Previous citations:
                %s

                Current user message:
                %s
                """.formatted(
                safe(prior.question()),
                summarize(prior.answer()),
                citations(prior),
                message
        ).trim();
    }

    boolean isFollowUpLike(String currentMessage) {
        String normalized = safe(currentMessage).trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return false;
        }
        if (SHORT_FOLLOW_UPS.contains(normalized)) {
            return true;
        }
        return FOLLOW_UP_MARKERS.stream().anyMatch(normalized::contains);
    }

    private double topScore(RetrievalResult result) {
        if (result == null || result.chunks().isEmpty()) {
            return 0.0;
        }
        return result.chunks().stream()
                .mapToDouble(RetrievedChunk::score)
                .max()
                .orElse(0.0);
    }

    private String summarize(String answer) {
        String value = safe(answer).replaceAll("\\s+", " ").trim();
        int maxLength = 800;
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }

    private String citations(ConversationRetrievalContext prior) {
        if (prior.citations().isEmpty()) {
            return "No prior citations were stored.";
        }
        return prior.citations().stream()
                .map(this::citation)
                .collect(Collectors.joining(", "));
    }

    private String citation(Citation citation) {
        return "%s:%d-%d".formatted(citation.filePath(), citation.startLine(), citation.endLine());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
