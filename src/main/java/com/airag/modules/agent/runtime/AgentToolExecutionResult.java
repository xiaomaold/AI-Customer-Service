package com.airag.modules.agent.runtime;

import com.airag.modules.agent.trace.AgentResultStatus;
import lombok.Builder;

@Builder
public record AgentToolExecutionResult(String outputValue,
                                       String outputSummary,
                                       AgentResultStatus resultStatus) {
}
