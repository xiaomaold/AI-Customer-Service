package com.airag.modules.agent.planner.impl;

import cn.hutool.core.util.StrUtil;
import com.airag.modules.agent.planner.AgentTaskPlanner;
import com.airag.modules.agent.task.AgentTask;
import com.airag.modules.agent.task.TaskPlan;
import com.airag.modules.agent.task.TaskStep;
import com.airag.modules.agent.task.TaskStepType;
import com.airag.modules.agent.task.TaskType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentTaskPlannerImpl implements AgentTaskPlanner {

    @Override
    public TaskPlan plan(AgentTask task) {
        TaskType taskType = task.taskType() == null ? TaskType.DIRECT_CHAT : task.taskType();
        List<TaskStep> steps = new ArrayList<>();
        boolean finalAnswerDirectly = false;
        String plannerReason = "ROUTE_DRIVEN";

        switch (taskType) {
            case DOCUMENT_QA -> {
                plannerReason = "DOCUMENT_QA_PLAN";
                steps.add(step(1, "document_evidence", TaskStepType.SEARCH_DOCUMENT,
                        "Search the active document before using broader context",
                        "knowledgeDocumentSearch", "documentEvidence", true));
                steps.add(step(2, "document_answer", TaskStepType.GENERATE_ANSWER,
                        "Answer strictly from the prepared document evidence",
                        "llmGeneration", "draftAnswer", true));
                steps.add(step(3, "document_validation", TaskStepType.VALIDATE_ANSWER,
                        "Validate that the answer stays grounded in the document",
                        "answerValidator", "validatedAnswer", true));
            }
            case STRUCTURED_FACT_QUERY -> {
                plannerReason = "STRUCTURED_FACT_PLAN";
                steps.add(step(1, "structured_fact_lookup", TaskStepType.SEARCH_STRUCTURED_FACT,
                        "Search structured fact candidates before any free-form generation",
                        "structuredFactSearch", "structuredFactEvidence", true));
                steps.add(step(2, "structured_fact_answer", TaskStepType.GENERATE_ANSWER,
                        "Return a concise field-oriented fact answer",
                        "llmGeneration", "draftAnswer", true));
                steps.add(step(3, "structured_fact_validation", TaskStepType.VALIDATE_ANSWER,
                        "Validate the answer format and supported facts",
                        "answerValidator", "validatedAnswer", true));
            }
            case KNOWLEDGE_QA -> {
                plannerReason = "KNOWLEDGE_QA_PLAN";
                steps.add(step(1, "knowledge_lookup", TaskStepType.SEARCH_KNOWLEDGE,
                        "Search knowledge base evidence before answering",
                        "knowledgeSearch", "knowledgeEvidence", true));
                steps.add(step(2, "knowledge_answer", TaskStepType.GENERATE_ANSWER,
                        "Synthesize the answer from the retrieved knowledge evidence",
                        "llmGeneration", "draftAnswer", true));
                steps.add(step(3, "knowledge_validation", TaskStepType.VALIDATE_ANSWER,
                        "Validate evidence grounding and answer consistency",
                        "answerValidator", "validatedAnswer", true));
            }
            case HYBRID_SYNTHESIS -> {
                plannerReason = "HYBRID_SYNTHESIS_PLAN";
                steps.add(step(1, "hybrid_knowledge_lookup", TaskStepType.SEARCH_KNOWLEDGE,
                        "Search policy or business evidence first",
                        "knowledgeSearch", "knowledgeEvidence", true));
                steps.add(step(2, "hybrid_document_lookup", TaskStepType.SEARCH_DOCUMENT,
                        "Optionally search document snippets to strengthen the draft",
                        "knowledgeDocumentSearch", "documentEvidence", false));
                steps.add(step(3, "hybrid_generation", TaskStepType.GENERATE_ANSWER,
                        "Generate the final answer or draft using the prepared evidence",
                        "llmGeneration", "draftAnswer", true));
                steps.add(step(4, "hybrid_validation", TaskStepType.VALIDATE_ANSWER,
                        "Validate the synthesized answer before returning it",
                        "answerValidator", "validatedAnswer", true));
            }
            case DIRECT_CHAT -> {
                steps.add(step(1, "direct_answer", TaskStepType.DIRECT_ANSWER,
                        "Answer directly without retrieval",
                        "generalGeneration", "finalAnswer", true));
                finalAnswerDirectly = true;
                plannerReason = "DIRECT_CHAT";
            }
            default -> {
                steps.add(step(1, "fallback_answer", TaskStepType.DIRECT_ANSWER,
                        "Fallback to direct answer",
                        "generalGeneration", "finalAnswer", true));
                finalAnswerDirectly = true;
                plannerReason = "FALLBACK";
            }
        }

        if (StrUtil.equals(task.routeReason(), "DOCUMENT_CARRYOVER")) {
            plannerReason = "DOCUMENT_CARRYOVER";
        }

        return TaskPlan.builder()
                .taskType(taskType)
                .steps(List.copyOf(steps))
                .finalAnswerDirectly(finalAnswerDirectly)
                .plannerReason(plannerReason)
                .build();
    }

    private TaskStep step(int order,
                          String stepName,
                          TaskStepType stepType,
                          String instruction,
                          String toolName,
                          String outputKey,
                          boolean required) {
        return TaskStep.builder()
                .stepOrder(order)
                .stepName(stepName)
                .stepType(stepType)
                .instruction(instruction)
                .toolName(toolName)
                .outputKey(outputKey)
                .required(required)
                .build();
    }
}
