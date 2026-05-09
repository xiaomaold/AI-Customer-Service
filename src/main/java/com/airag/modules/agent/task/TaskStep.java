package com.airag.modules.agent.task;

import lombok.Builder;

@Builder
public record TaskStep(int stepOrder,
                       String stepName,
                       TaskStepType stepType,
                       String instruction,
                       String toolName,
                       String outputKey,
                       boolean required) {
}
