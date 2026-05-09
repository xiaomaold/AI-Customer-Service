package com.airag.modules.agent.runtime;

import lombok.Builder;

import java.util.Map;

@Builder
public record AgentStepExecutionResult(Map<String, String> stepOutputs,
                                       String appendedExecutionDirective) {
}
