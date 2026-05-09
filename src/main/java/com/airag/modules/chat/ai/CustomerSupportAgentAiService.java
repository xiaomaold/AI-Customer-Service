package com.airag.modules.chat.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface CustomerSupportAgentAiService {

    @SystemMessage("{{systemPrompt}}")
    @UserMessage("""
            【聊天历史】
            {{history}}

            【当前问题】
            {{question}}
            """)
    TokenStream chat(@V("systemPrompt") String systemPrompt,
                     @V("history") String history,
                     @V("question") String question);
}
