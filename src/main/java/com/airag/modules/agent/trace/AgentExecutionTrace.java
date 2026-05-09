package com.airag.modules.agent.trace;

import lombok.Builder;

@Builder
public record AgentExecutionTrace(String stage,
                                  String traceId,
                                  Long sessionId,
                                  Long userId,
                                  String taskType,
                                  String plannerReason,
                                  String strategyName,
                                  String executionProfile,
                                  Integer stepOrder,
                                  String stepName,
                                  String stepType,
                                  String toolName,
                                  String outputKey,
                                  String inputSummary,
                                  String outputSummary,
                                  AgentResultStatus resultStatus,
                                  String status,
                                  boolean knowledgeEvidenceUsed,
                                  boolean documentEvidenceUsed,
                                  boolean directAnswerUsed,
                                  boolean answerReplaced) {
}
