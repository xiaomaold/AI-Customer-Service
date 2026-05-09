package com.airag.modules.agent.service;

import com.airag.modules.chat.ai.CustomerSupportAgentAiService;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AgentStreamingService {

    String streamAnswer(CustomerSupportAgentAiService agentAiService,
                        String systemPrompt,
                        String history,
                        String question,
                        SseEmitter emitter,
                        boolean streamToClient) throws InterruptedException;
}
