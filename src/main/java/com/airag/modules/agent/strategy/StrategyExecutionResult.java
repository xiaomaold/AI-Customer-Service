package com.airag.modules.agent.strategy;

import com.airag.modules.agent.trace.AgentResultStatus;
import lombok.Builder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Builder
public record StrategyExecutionResult(SseEmitter emitter,
                                      AgentResultStatus resultStatus,
                                      String outputSummary) {
}
