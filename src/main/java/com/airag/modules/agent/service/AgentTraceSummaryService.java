package com.airag.modules.agent.service;

import com.airag.modules.agent.trace.AgentExecutionTrace;

import java.util.List;

public interface AgentTraceSummaryService {

    String summarize(List<AgentExecutionTrace> traces);
}
