package com.airag.modules.agent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.airag.modules.agent.service.AgentTraceSummaryService;
import com.airag.modules.agent.trace.AgentExecutionTrace;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class AgentTraceSummaryServiceImpl implements AgentTraceSummaryService {

    @Override
    public String summarize(List<AgentExecutionTrace> traces) {
        if (traces == null || traces.isEmpty()) {
            return "No execution trace recorded.";
        }

        AgentExecutionTrace first = traces.get(0);
        long plannedSteps = traces.stream().filter(trace -> "STEP_PLANNED".equals(trace.stage())).count();
        String strategy = traces.stream()
                .map(AgentExecutionTrace::strategyName)
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse("UNKNOWN");
        String resultStatus = traces.stream()
                .map(AgentExecutionTrace::resultStatus)
                .filter(java.util.Objects::nonNull)
                .map(Enum::name)
                .reduce((left, right) -> right)
                .orElse("UNKNOWN");
        String stepSummary = traces.stream()
                .filter(trace -> StrUtil.isNotBlank(trace.stepName()))
                .sorted(Comparator.comparing(trace -> trace.stepOrder() == null ? Integer.MAX_VALUE : trace.stepOrder()))
                .map(trace -> {
                    String status = trace.resultStatus() != null ? trace.resultStatus().name() : StrUtil.blankToDefault(trace.status(), "UNKNOWN");
                    return trace.stepName() + "(" + status + ")";
                })
                .distinct()
                .reduce((left, right) -> left + " -> " + right)
                .orElse("none");

        return "traceId=" + StrUtil.blankToDefault(first.traceId(), "")
                + ", taskType=" + StrUtil.blankToDefault(first.taskType(), "UNKNOWN")
                + ", plannerReason=" + StrUtil.blankToDefault(first.plannerReason(), "UNKNOWN")
                + ", strategy=" + strategy
                + ", plannedSteps=" + plannedSteps
                + ", latestResult=" + resultStatus
                + ", stepFlow=" + stepSummary;
    }
}
