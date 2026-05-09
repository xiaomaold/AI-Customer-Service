package com.airag.modules.agent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.airag.modules.agent.service.AgentResponseOrchestrationService;
import com.airag.modules.agent.service.AgentStreamingService;
import com.airag.modules.chat.ai.CustomerSupportAgentAiService;
import dev.langchain4j.service.TokenStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentStreamingServiceImpl implements AgentStreamingService {

    private static final long FIRST_TOKEN_TIMEOUT_MS = 30_000L;
    private static final long STALL_TIMEOUT_MS = 15_000L;
    private static final long MAX_GENERATION_TIMEOUT_MS = 120_000L;

    private final AgentResponseOrchestrationService agentResponseOrchestrationService;

    @Override
    public String streamAnswer(CustomerSupportAgentAiService agentAiService,
                               String systemPrompt,
                               String history,
                               String question,
                               SseEmitter emitter,
                               boolean streamToClient) throws InterruptedException {
        StringBuilder answerBuilder = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicBoolean streamFailed = new AtomicBoolean(false);
        AtomicBoolean hasToken = new AtomicBoolean(false);
        AtomicLong lastTokenAt = new AtomicLong(System.currentTimeMillis());
        long startAt = System.currentTimeMillis();

        TokenStream tokenStream = agentAiService.chat(systemPrompt, history, question);
        tokenStream.onPartialResponse(token -> {
                    answerBuilder.append(token);
                    hasToken.set(true);
                    lastTokenAt.set(System.currentTimeMillis());
                    if (streamToClient && emitter != null && !streamFailed.get()) {
                        try {
                            agentResponseOrchestrationService.sendText(emitter, token);
                        } catch (RuntimeException exception) {
                            streamFailed.set(true);
                            errorRef.set(exception);
                            latch.countDown();
                        }
                    }
                })
                .onCompleteResponse(response -> latch.countDown())
                .onError(error -> {
                    errorRef.set(error);
                    latch.countDown();
                })
                .start();

        while (true) {
            if (latch.await(1, TimeUnit.SECONDS)) {
                break;
            }

            long now = System.currentTimeMillis();
            if (!hasToken.get() && now - startAt > FIRST_TOKEN_TIMEOUT_MS) {
                throw new RuntimeException("Agent first token timeout");
            }
            if (hasToken.get() && now - lastTokenAt.get() > STALL_TIMEOUT_MS) {
                log.warn("Agent stream stalled question={}, partialAnswer={}",
                        StrUtil.blankToDefault(question, ""),
                        StrUtil.subPre(answerBuilder.toString(), 120));
                break;
            }
            if (now - startAt > MAX_GENERATION_TIMEOUT_MS) {
                throw new RuntimeException("Agent answer generation timed out");
            }
        }

        if (errorRef.get() != null) {
            throw new RuntimeException(errorRef.get());
        }
        return answerBuilder.toString().trim();
    }
}
