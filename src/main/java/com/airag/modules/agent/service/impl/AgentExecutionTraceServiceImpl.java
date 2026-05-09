package com.airag.modules.agent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.airag.modules.agent.service.AgentExecutionTraceService;
import com.airag.modules.agent.trace.AgentExecutionTrace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AgentExecutionTraceServiceImpl implements AgentExecutionTraceService {

    @Override
    public void trace(AgentExecutionTrace trace) {
        if (trace == null) {
            return;
        }
        log.info("Agent trace stage={}, traceId={}, sessionId={}, userId={}, taskType={}, plannerReason={}, strategy={}, executionProfile={}, stepOrder={}, stepName={}, stepType={}, toolName={}, outputKey={}, inputSummary={}, outputSummary={}, resultStatus={}, status={}, knowledgeEvidenceUsed={}, documentEvidenceUsed={}, directAnswerUsed={}, answerReplaced={}",
                StrUtil.blankToDefault(trace.stage(), ""),
                StrUtil.blankToDefault(trace.traceId(), ""),
                trace.sessionId(),
                trace.userId(),
                StrUtil.blankToDefault(trace.taskType(), ""),
                StrUtil.blankToDefault(trace.plannerReason(), ""),
                StrUtil.blankToDefault(trace.strategyName(), ""),
                StrUtil.blankToDefault(trace.executionProfile(), ""),
                trace.stepOrder(),
                StrUtil.blankToDefault(trace.stepName(), ""),
                StrUtil.blankToDefault(trace.stepType(), ""),
                StrUtil.blankToDefault(trace.toolName(), ""),
                StrUtil.blankToDefault(trace.outputKey(), ""),
                StrUtil.blankToDefault(trace.inputSummary(), ""),
                StrUtil.blankToDefault(trace.outputSummary(), ""),
                trace.resultStatus(),
                StrUtil.blankToDefault(trace.status(), ""),
                trace.knowledgeEvidenceUsed(),
                trace.documentEvidenceUsed(),
                trace.directAnswerUsed(),
                trace.answerReplaced());
    }
}
