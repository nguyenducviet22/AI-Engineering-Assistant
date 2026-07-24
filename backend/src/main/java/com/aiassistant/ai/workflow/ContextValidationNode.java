package com.aiassistant.ai.workflow;

import com.aiassistant.retrieval.RetrievedChunk;
import java.util.Comparator;
import java.util.List;

public class ContextValidationNode implements RepositoryChatNode {
    static final double TOP_SCORE_THRESHOLD = 0.32;
    static final double AVERAGE_TOP_THREE_THRESHOLD = 0.27;
    static final double SINGLE_STRONG_CHUNK_THRESHOLD = 0.50;

    @Override
    public RepositoryChatState apply(RepositoryChatState state) {
        List<RetrievedChunk> chunks = state.retrievalResult().chunks().stream()
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .toList();
        boolean valid = isGoodEnough(chunks);
        RepositoryChatState next = state.withContextValidation(valid);
        if (!valid) {
            next = next.withRefusal(RepositoryChatState.INSUFFICIENT_CONTEXT_MESSAGE);
        }
        return next.mark(step());
    }

    private boolean isGoodEnough(List<RetrievedChunk> chunks) {
        if (chunks.isEmpty()) {
            return false;
        }
        double topScore = chunks.getFirst().score();
        if (topScore >= SINGLE_STRONG_CHUNK_THRESHOLD) {
            return true;
        }
        if (chunks.size() < 2 || topScore < TOP_SCORE_THRESHOLD) {
            return false;
        }
        int count = Math.min(3, chunks.size());
        double averageTop = chunks.stream()
                .limit(count)
                .mapToDouble(RetrievedChunk::score)
                .average()
                .orElse(0.0);
        return averageTop >= AVERAGE_TOP_THREE_THRESHOLD;
    }

    @Override
    public WorkflowStep step() {
        return WorkflowStep.CONTEXT_VALIDATION;
    }
}
