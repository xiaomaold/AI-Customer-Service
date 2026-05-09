package com.airag.modules.agent.service.impl;

import com.airag.modules.agent.trace.AgentExecutionTrace;
import com.airag.modules.agent.trace.AgentResultStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTraceSummaryServiceImplTest {

    private final AgentTraceSummaryServiceImpl service = new AgentTraceSummaryServiceImpl();

    @Test
    void shouldSummarizeExecutionTraceFlow() {
        List<AgentExecutionTrace> traces = List.of(
                AgentExecutionTrace.builder()
                        .stage("PLAN_READY")
                        .traceId("trace-1")
                        .taskType("DOCUMENT_QA")
                        .plannerReason("DOCUMENT_CARRYOVER")
                        .build(),
                AgentExecutionTrace.builder()
                        .stage("STEP_PLANNED")
                        .traceId("trace-1")
                        .taskType("DOCUMENT_QA")
                        .plannerReason("DOCUMENT_CARRYOVER")
                        .stepOrder(1)
                        .stepName("document_evidence")
                        .status("REQUIRED")
                        .build(),
                AgentExecutionTrace.builder()
                        .stage("STRATEGY_SELECTED")
                        .traceId("trace-1")
                        .strategyName("DOCUMENT_QA")
                        .build(),
                AgentExecutionTrace.builder()
                        .stage("STEP_COMPLETED")
                        .traceId("trace-1")
                        .stepOrder(1)
                        .stepName("document_evidence")
                        .resultStatus(AgentResultStatus.STREAM_STARTED)
                        .build()
        );

        String summary = service.summarize(traces);

        assertTrue(summary.contains("traceId=trace-1"));
        assertTrue(summary.contains("taskType=DOCUMENT_QA"));
        assertTrue(summary.contains("strategy=DOCUMENT_QA"));
        assertTrue(summary.contains("plannedSteps=1"));
        assertTrue(summary.contains("latestResult=STREAM_STARTED"));
        assertTrue(summary.contains("document_evidence"));
    }
}
