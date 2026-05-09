package com.airag.modules.agent.runtime.tool;

import com.airag.modules.agent.runtime.AgentTool;
import com.airag.modules.agent.runtime.AgentToolExecutionContext;
import com.airag.modules.agent.runtime.AgentToolExecutionResult;
import com.airag.modules.agent.service.KnowledgeAgentFacade;
import com.airag.modules.agent.task.TaskStep;
import com.airag.modules.agent.task.TaskStepType;
import com.airag.modules.agent.trace.AgentResultStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StructuredFactAgentTool implements AgentTool {

    private final KnowledgeAgentFacade knowledgeAgentFacade;

    @Override
    public boolean supports(TaskStep step) {
        return step.stepType() == TaskStepType.SEARCH_STRUCTURED_FACT
                || "structuredFactSearch".equalsIgnoreCase(step.toolName());
    }

    @Override
    public String toolName() {
        return "structuredFactSearch";
    }

    @Override
    public AgentToolExecutionResult execute(AgentToolExecutionContext context, TaskStep step) {
        String result = knowledgeAgentFacade.searchStructuredFact(
                context.loginUser(),
                context.question(),
                context.knowledgeBaseKeyword(),
                3
        );
        return AgentToolExecutionResult.builder()
                .outputValue(result)
                .outputSummary("Prepared structured fact evidence via " + toolName())
                .resultStatus(AgentResultStatus.COMPLETED)
                .build();
    }
}
