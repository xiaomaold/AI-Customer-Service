package com.airag.modules.agent.executor.impl;

import cn.hutool.core.util.StrUtil;
import com.airag.modules.agent.runtime.AgentStepExecutor;
import com.airag.modules.agent.executor.AgentTaskExecutor;
import com.airag.modules.agent.service.AgentExecutionTraceService;
import com.airag.modules.agent.strategy.AgentExecutionStrategy;
import com.airag.modules.agent.strategy.StrategyExecutionResult;
import com.airag.modules.agent.trace.AgentResultStatus;
import com.airag.modules.agent.task.AgentTask;
import com.airag.modules.agent.task.TaskPlan;
import com.airag.modules.agent.trace.AgentExecutionTrace;
import com.airag.modules.auth.security.LoginUser;
import com.airag.modules.chat.dto.ChatSendRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentTaskExecutorImpl implements AgentTaskExecutor {

    private final List<AgentExecutionStrategy> executionStrategies;
    private final AgentStepExecutor agentStepExecutor;
    private final AgentExecutionTraceService agentExecutionTraceService;

    @Override
    public SseEmitter execute(AgentTask task, TaskPlan plan, ChatSendRequest request, LoginUser loginUser) {
        agentStepExecutor.execute(task, plan, request, loginUser);
        AgentExecutionStrategy strategy = executionStrategies.stream()
                .filter(candidate -> candidate.supports(task, plan))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No execution strategy for taskType=" + task.taskType()));

        agentExecutionTraceService.trace(AgentExecutionTrace.builder()
                .stage("STRATEGY_SELECTED")
                .traceId(request.getExecutionTraceId())
                .sessionId(task.sessionId())
                .userId(task.userId())
                .taskType(task.taskType().name())
                .plannerReason(StrUtil.blankToDefault(plan.plannerReason(), ""))
                .strategyName(strategy.strategyName())
                .executionProfile(request.getExecutionProfile())
                .build());
        StrategyExecutionResult executionResult = strategy.execute(task, plan, request, loginUser);
        agentExecutionTraceService.trace(AgentExecutionTrace.builder()
                .stage("STRATEGY_EXECUTED")
                .traceId(request.getExecutionTraceId())
                .sessionId(task.sessionId())
                .userId(task.userId())
                .taskType(task.taskType().name())
                .plannerReason(StrUtil.blankToDefault(plan.plannerReason(), ""))
                .strategyName(strategy.strategyName())
                .executionProfile(request.getExecutionProfile())
                .outputSummary(executionResult.outputSummary())
                .resultStatus(executionResult.resultStatus())
                .status("HANDOFF_RETURNED")
                .build());
        return executionResult.emitter();
    }
}
