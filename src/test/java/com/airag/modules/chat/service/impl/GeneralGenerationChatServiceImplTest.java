package com.airag.modules.chat.service.impl;

import com.airag.modules.agent.query.KnowledgeQueryPlanner;
import com.airag.modules.chat.ai.GeneralGenerationAiService;
import com.airag.modules.chat.dto.ChatSendRequest;
import com.airag.modules.chat.enums.ChatRouteModeEnum;
import com.airag.modules.chat.prompt.PromptTemplateService;
import com.airag.modules.chat.service.ChatAnswerPresentationService;
import com.airag.modules.chat.service.ChatMessageService;
import com.airag.modules.chat.service.ChatSessionService;
import com.airag.modules.chat.service.KnowledgeRetrieverService;
import com.airag.modules.knowledge.service.KnowledgeBaseService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class GeneralGenerationChatServiceImplTest {

    @Mock
    private ChatSessionService chatSessionService;

    @Mock
    private ChatMessageService chatMessageService;

    @Mock
    private ChatAnswerPresentationService chatAnswerPresentationService;

    @Mock
    private GeneralGenerationAiService generalGenerationAiService;

    @Mock
    private KnowledgeRetrieverService knowledgeRetrieverService;

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private PromptTemplateService promptTemplateService;

    @Mock
    private KnowledgeQueryPlanner knowledgeQueryPlanner;

    @Mock
    private Executor sseExecutor;

    @InjectMocks
    private GeneralGenerationChatServiceImpl generalGenerationChatService;

    @Test
    void shouldHideKnowledgeBaseReferenceForGeneralGeneration() throws Exception {
        ChatSendRequest request = request("GENERAL_GENERATION");
        String referenceContent = buildReferenceContent(request, "kb");

        assertFalse(referenceContent.contains("REFERENCE_COUNT"));
        assertFalse(referenceContent.contains("REFERENCE_1"));
    }

    @Test
    void shouldKeepKnowledgeBaseReferenceForHybridGeneration() throws Exception {
        ChatSendRequest request = request("HYBRID_GENERATION");
        String referenceContent = buildReferenceContent(request, "kb");

        assertTrue(referenceContent.contains("REFERENCE_COUNT: 1"));
        assertTrue(referenceContent.contains("REFERENCE_1: kb"));
    }

    @Test
    void shouldNotRetrieveKnowledgeForGeneralFollowUpContinuation() throws Exception {
        ChatSendRequest request = request("GENERAL_GENERATION");
        request.setRouteReason("FOLLOW_UP_CONTINUATION");
        request.setRouteDomain("CONTACT_AND_CHANNEL");
        request.setRouteIntent("STRUCTURED_FACT_QUERY");

        boolean shouldRetrieve = shouldRetrieveKnowledge(request, ChatRouteModeEnum.GENERAL_GENERATION);

        assertFalse(shouldRetrieve);
    }

    private ChatSendRequest request(String routeMode) {
        ChatSendRequest request = new ChatSendRequest();
        request.setSessionId(1L);
        request.setUserId(1001L);
        request.setQuestion("test");
        request.setRouteMode(routeMode);
        request.setRouteReason("TEST_REASON");
        request.setRouteDomain("OTHER");
        request.setRouteIntent("KNOWLEDGE_QA");
        return request;
    }

    private String buildReferenceContent(ChatSendRequest request, String preferredKnowledgeBaseName) throws Exception {
        Class<?> retrievalSelectionClass = Class.forName(
                "com.airag.modules.chat.service.impl.GeneralGenerationChatServiceImpl$RetrievalSelection"
        );
        Constructor<?> constructor = retrievalSelectionClass.getDeclaredConstructor(List.class, String.class);
        constructor.setAccessible(true);
        Object retrievalSelection = constructor.newInstance(List.of(), preferredKnowledgeBaseName);

        Method method = GeneralGenerationChatServiceImpl.class.getDeclaredMethod(
                "buildReferenceContent",
                ChatSendRequest.class,
                retrievalSelectionClass
        );
        method.setAccessible(true);
        return (String) method.invoke(generalGenerationChatService, request, retrievalSelection);
    }

    private boolean shouldRetrieveKnowledge(ChatSendRequest request, ChatRouteModeEnum routeMode) throws Exception {
        Method method = GeneralGenerationChatServiceImpl.class.getDeclaredMethod(
                "shouldRetrieveKnowledge",
                ChatSendRequest.class,
                ChatRouteModeEnum.class
        );
        method.setAccessible(true);
        return (boolean) method.invoke(generalGenerationChatService, request, routeMode);
    }
}
