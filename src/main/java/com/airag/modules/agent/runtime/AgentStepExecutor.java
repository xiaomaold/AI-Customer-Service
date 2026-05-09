package com.airag.modules.agent.runtime;

import cn.hutool.core.util.StrUtil;
import com.airag.modules.agent.service.AgentExecutionTraceService;
import com.airag.modules.agent.task.AgentTask;
import com.airag.modules.agent.task.TaskPlan;
import com.airag.modules.agent.task.TaskStep;
import com.airag.modules.agent.trace.AgentExecutionTrace;
import com.airag.modules.agent.trace.AgentResultStatus;
import com.airag.modules.auth.security.LoginUser;
import com.airag.modules.chat.dto.ChatSendRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

@Service
@RequiredArgsConstructor
public class AgentStepExecutor {

    private final AgentToolRegistry agentToolRegistry;
    private final AgentExecutionTraceService agentExecutionTraceService;

    public AgentStepExecutionResult execute(AgentTask task, TaskPlan plan, ChatSendRequest request, LoginUser loginUser) {
        Map<String, String> stepOutputs = new LinkedHashMap<>();
        StringJoiner directiveJoiner = new StringJoiner("\n\n");

        for (TaskStep step : plan.steps()) {
            traceStepStart(task, plan, request, step);

            AgentToolExecutionContext context = new AgentToolExecutionContext(task, plan, request, loginUser, stepOutputs);
            AgentToolExecutionResult result = agentToolRegistry.find(step)
                    .map(tool -> tool.execute(context, step))
                    .orElseGet(() -> AgentToolExecutionResult.builder()
                            .outputSummary("Deferred to downstream strategy execution")
                            .resultStatus(AgentResultStatus.SKIPPED)
                            .build());

            if (StrUtil.isNotBlank(step.outputKey()) && StrUtil.isNotBlank(result.outputValue())) {
                stepOutputs.put(step.outputKey(), result.outputValue());
            }
            if (StrUtil.isNotBlank(result.outputValue())) {
                directiveJoiner.add(formatDirectiveBlock(step, result.outputValue()));
            }
            traceStepCompleted(task, plan, request, step, result);
        }

        String appendedExecutionDirective = directiveJoiner.length() == 0 ? null : directiveJoiner.toString();
        if (StrUtil.isNotBlank(appendedExecutionDirective)) {
            request.setExecutionDirective(mergeExecutionDirective(request.getExecutionDirective(), appendedExecutionDirective));
        }

        return AgentStepExecutionResult.builder()
                .stepOutputs(Map.copyOf(stepOutputs))
                .appendedExecutionDirective(appendedExecutionDirective)
                .build();
    }

    private void traceStepStart(AgentTask task, TaskPlan plan, ChatSendRequest request, TaskStep step) {
        agentExecutionTraceService.trace(AgentExecutionTrace.builder()
                .stage("STEP_STARTED")
                .traceId(request.getExecutionTraceId())
                .sessionId(task.sessionId())
                .userId(task.userId())
                .taskType(task.taskType().name())
                .plannerReason(StrUtil.blankToDefault(plan.plannerReason(), ""))
                .executionProfile(request.getExecutionProfile())
                .stepOrder(step.stepOrder())
                .stepName(step.stepName())
                .stepType(step.stepType().name())
                .toolName(step.toolName())
                .outputKey(step.outputKey())
                .inputSummary(step.instruction())
                .resultStatus(AgentResultStatus.DISPATCHED)
                .status("STEP_DISPATCHED")
                .build());
    }

    private void traceStepCompleted(AgentTask task,
                                    TaskPlan plan,
                                    ChatSendRequest request,
                                    TaskStep step,
                                    AgentToolExecutionResult result) {
        agentExecutionTraceService.trace(AgentExecutionTrace.builder()
                .stage("STEP_COMPLETED")
                .traceId(request.getExecutionTraceId())
                .sessionId(task.sessionId())
                .userId(task.userId())
                .taskType(task.taskType().name())
                .plannerReason(StrUtil.blankToDefault(plan.plannerReason(), ""))
                .executionProfile(request.getExecutionProfile())
                .stepOrder(step.stepOrder())
                .stepName(step.stepName())
                .stepType(step.stepType().name())
                .toolName(step.toolName())
                .outputKey(step.outputKey())
                .inputSummary(step.instruction())
                .outputSummary(StrUtil.blankToDefault(result.outputSummary(), ""))
                .resultStatus(result.resultStatus())
                .status(resolveCompletedStatus(result.resultStatus()))
                .build());
    }

    private String resolveCompletedStatus(AgentResultStatus resultStatus) {
        if (resultStatus == null) {
            return "UNKNOWN";
        }
        return switch (resultStatus) {
            case COMPLETED -> "STEP_EXECUTED";
            case FAILED -> "STEP_FAILED";
            case SKIPPED -> "STEP_DEFERRED";
            default -> "STEP_FINISHED";
        };
    }

    private String mergeExecutionDirective(String currentDirective, String appendedExecutionDirective) {
        if (StrUtil.isBlank(currentDirective)) {
            return appendedExecutionDirective;
        }
        return currentDirective.trim() + "\n\n" + appendedExecutionDirective;
    }

    private String formatDirectiveBlock(TaskStep step, String outputValue) {
        return new StringBuilder("[Pre-executed step result]")
                .append("\nstep_name: ").append(StrUtil.blankToDefault(step.stepName(), ""))
                .append("\nstep_type: ").append(step.stepType() == null ? "" : step.stepType().name())
                .append("\ntool_name: ").append(StrUtil.blankToDefault(step.toolName(), ""))
                .append("\noutput_key: ").append(StrUtil.blankToDefault(step.outputKey(), ""))
                .append("\nresult:")
                .append("\n")
                .append(outputValue)
                .toString();
    }
}
