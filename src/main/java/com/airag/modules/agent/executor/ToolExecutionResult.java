package com.airag.modules.agent.executor;

import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public record ToolExecutionResult(String toolName,
                                  boolean success,
                                  String resultType,
                                  String summary,
                                  String rawContent,
                                  List<String> references,
                                  Map<String, Object> metadata) {
}
