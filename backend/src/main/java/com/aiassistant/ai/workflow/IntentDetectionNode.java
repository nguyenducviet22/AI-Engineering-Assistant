package com.aiassistant.ai.workflow;

import java.util.Locale;

public class IntentDetectionNode implements RepositoryChatNode {
    @Override
    public RepositoryChatState apply(RepositoryChatState state) {
        String message = state.userMessage() == null ? "" : state.userMessage().toLowerCase(Locale.ROOT);
        RepositoryChatIntent intent;
        if (message.contains("readme") || message.contains("documentation") || message.contains("document")) {
            intent = RepositoryChatIntent.DOCUMENTATION;
        } else if (message.contains("class") || message.contains("method") || message.contains("explain")) {
            intent = RepositoryChatIntent.CODE_EXPLANATION;
        } else {
            intent = RepositoryChatIntent.REPOSITORY_CHAT;
        }
        return state.withIntent(intent).mark(step());
    }

    @Override
    public WorkflowStep step() {
        return WorkflowStep.INTENT_DETECTION;
    }
}
