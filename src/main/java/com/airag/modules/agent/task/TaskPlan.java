package com.airag.modules.agent.task;

import lombok.Builder;

import java.util.List;

@Builder
public record TaskPlan(TaskType taskType,
                       List<TaskStep> steps,
                       boolean finalAnswerDirectly,
                       String plannerReason) {
}
