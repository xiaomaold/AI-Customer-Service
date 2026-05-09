package com.airag.modules.chat.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface AiDocumentAnalysisAiService {

    @SystemMessage("{{systemPrompt}}")
    @UserMessage("""
            [Task Type]
            {{taskType}}

            [User Instruction]
            {{instruction}}

            [Original File Name]
            {{fileName}}

            [Requested Knowledge Base]
            {{targetKnowledgeBase}}

            [Accessible Knowledge Bases]
            {{knowledgeBases}}

            [Document Preview]
            {{contentPreview}}
            """)
    TokenStream analyze(@V("systemPrompt") String systemPrompt,
                        @V("taskType") String taskType,
                        @V("instruction") String instruction,
                        @V("fileName") String fileName,
                        @V("targetKnowledgeBase") String targetKnowledgeBase,
                        @V("knowledgeBases") String knowledgeBases,
                        @V("contentPreview") String contentPreview);
}
