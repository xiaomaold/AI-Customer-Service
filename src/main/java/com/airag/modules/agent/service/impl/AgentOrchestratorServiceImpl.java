package com.airag.modules.agent.service.impl;

import cn.hutool.core.util.IdUtil;
import com.airag.modules.agent.context.RecentConversationContext;
import com.airag.modules.agent.executor.AgentTaskExecutor;
import com.airag.modules.agent.planner.AgentTaskPlanner;
import com.airag.modules.agent.service.AgentExecutionTraceService;
import com.airag.modules.agent.service.AgentOrchestratorService;
import com.airag.modules.agent.service.AgentTaskBuilder;
import com.airag.modules.agent.service.ConversationContextResolver;
import com.airag.modules.agent.task.AgentTask;
import com.airag.modules.agent.task.TaskPlan;
import com.airag.modules.agent.trace.AgentExecutionTrace;
import com.airag.modules.auth.security.LoginUser;
import com.airag.modules.chat.dto.ChatSendRequest;
import com.airag.modules.chat.entity.ChatMessage;
import com.airag.modules.chat.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestratorServiceImpl implements AgentOrchestratorService {

    private final ChatMessageService chatMessageService;
    private final ConversationContextResolver conversationContextResolver;
    private final AgentTaskBuilder agentTaskBuilder;
    private final AgentTaskPlanner agentTaskPlanner;
    private final AgentExecutionTraceService agentExecutionTraceService;
    private final AgentTaskExecutor agentTaskExecutor;

    @Value("${rag.chat.history-limit:10}")
    private Integer historyLimit;

    @Override
    public SseEmitter streamChat(ChatSendRequest request, LoginUser loginUser) {
        if (request.getExecutionTraceId() == null || request.getExecutionTraceId().isBlank()) {
            request.setExecutionTraceId(IdUtil.fastSimpleUUID());
        }
        List<ChatMessage> recentMessages = chatMessageService.listRecentMessages(request.getSessionId(), historyLimit);
        RecentConversationContext conversationContext = conversationContextResolver.resolve(
                loginUser,
                request.getSessionId(),
                request.getKnowledgeBaseId(),
                recentMessages,
                request.getQuestion(),
                request.getCarryoverKnowledgeBaseName(),
                request.getCarryoverDocumentName()
        );
        AgentTask task = agentTaskBuilder.build(request, conversationContext);
        TaskPlan plan = agentTaskPlanner.plan(task);

        log.info("Agent orchestrator planned sessionId={}, userId={}, taskType={}, plannerReason={}, stepCount={}",
                request.getSessionId(),
                request.getUserId(),
                task.taskType(),
                plan.plannerReason(),
                plan.steps().size());
        agentExecutionTraceService.trace(AgentExecutionTrace.builder()
                .stage("PLAN_READY")
                .traceId(request.getExecutionTraceId())
                .sessionId(request.getSessionId())
                .userId(request.getUserId())
                .taskType(task.taskType().name())
                .plannerReason(plan.plannerReason())
                .executionProfile(request.getExecutionProfile())
                .build());
        plan.steps().forEach(step -> agentExecutionTraceService.trace(AgentExecutionTrace.builder()
                .stage("STEP_PLANNED")
                .traceId(request.getExecutionTraceId())
                .sessionId(request.getSessionId())
                .userId(request.getUserId())
                .taskType(task.taskType().name())
                .plannerReason(plan.plannerReason())
                .executionProfile(request.getExecutionProfile())
                .stepOrder(step.stepOrder())
                .stepName(step.stepName())
                .stepType(step.stepType().name())
                .toolName(step.toolName())
                .outputKey(step.outputKey())
                .status(step.required() ? "REQUIRED" : "OPTIONAL")
                .build()));

        return agentTaskExecutor.execute(task, plan, request, loginUser);
    }
}
