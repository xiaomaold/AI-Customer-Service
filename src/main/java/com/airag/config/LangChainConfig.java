package com.airag.config;

import com.airag.modules.chat.ai.CustomerSupportAiService;
import com.airag.modules.chat.ai.GeneralGenerationAiService;
import com.airag.modules.chat.ai.AiDocumentAnalysisAiService;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LangChainConfig {

    @Bean
    public CustomerSupportAiService customerSupportAiService(StreamingChatModel streamingChatModel) {
        return AiServices.builder(CustomerSupportAiService.class)
                .streamingChatModel(streamingChatModel)
                .build();
    }

    @Bean
    public GeneralGenerationAiService generalGenerationAiService(StreamingChatModel streamingChatModel) {
        return AiServices.builder(GeneralGenerationAiService.class)
                .streamingChatModel(streamingChatModel)
                .build();
    }

    @Bean
    public AiDocumentAnalysisAiService aiDocumentAnalysisAiService(StreamingChatModel streamingChatModel) {
        return AiServices.builder(AiDocumentAnalysisAiService.class)
                .streamingChatModel(streamingChatModel)
                .build();
    }
}
