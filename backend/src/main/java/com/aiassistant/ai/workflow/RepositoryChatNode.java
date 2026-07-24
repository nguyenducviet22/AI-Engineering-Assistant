package com.aiassistant.ai.workflow;

public interface RepositoryChatNode {
    RepositoryChatState apply(RepositoryChatState state);

    WorkflowStep step();
}
