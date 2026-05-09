package com.airag.modules.agent.service.impl;

import com.airag.modules.agent.context.RecentConversationContext;
import com.airag.modules.agent.service.AgentEvidenceBundle;
import com.airag.modules.agent.service.AgentQuestionClassifier;
import com.airag.modules.agent.service.KnowledgeAgentFacade;
import com.airag.modules.auth.security.LoginUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentEvidencePreparationServiceImplTest {

    @Mock
    private AgentQuestionClassifier agentQuestionClassifier;

    @Mock
    private KnowledgeAgentFacade knowledgeAgentFacade;

    @Test
    void shouldReturnOriginalQuestionWhenNoEvidenceAndNoStrictRequirement() {
        AgentEvidencePreparationServiceImpl service =
                new AgentEvidencePreparationServiceImpl(agentQuestionClassifier, knowledgeAgentFacade);
        LoginUser loginUser = LoginUser.builder().userId(1001L).build();

        when(agentQuestionClassifier.resolveKnowledgeBaseKeyword(eq("apple"), any())).thenReturn(null);
        when(agentQuestionClassifier.resolveDocumentKeyword(eq("apple"), any())).thenReturn(null);
        when(agentQuestionClassifier.isStructuredFactQuestion("apple")).thenReturn(false);
        when(knowledgeAgentFacade.searchKnowledge(loginUser, "apple", null, 3)).thenReturn("MATCHED_COUNT: 0");

        AgentEvidenceBundle bundle = service.prepare(
                loginUser,
                RecentConversationContext.empty(),
                null,
                "GENERAL_GENERATION",
                "GENERAL_REQUEST",
                "OTHER",
                "KNOWLEDGE_QA",
                "DIRECT_CHAT",
                null,
                "apple"
        );

        assertEquals("apple", bundle.effectiveQuestion());
        assertNull(bundle.directAnswer());
    }

    @Test
    void shouldBuildDirectStructuredFactAnswerFromStructuredEvidence() {
        AgentEvidencePreparationServiceImpl service =
                new AgentEvidencePreparationServiceImpl(agentQuestionClassifier, knowledgeAgentFacade);
        LoginUser loginUser = LoginUser.builder().userId(1001L).build();
        String structuredEvidence = """
                RESULT_TYPE: STRUCTURED_FACT_SEARCH
                ITEM_1
                document_name: contact-us
                fact_type: PHONE
                fact_label: hotline
                extracted_value: 400-123-4567
                snippet: hotline
                """;

        when(agentQuestionClassifier.resolveKnowledgeBaseKeyword(eq("phone"), any())).thenReturn("customer-service-kb");
        when(agentQuestionClassifier.resolveDocumentKeyword(eq("phone"), any())).thenReturn(null);
        when(knowledgeAgentFacade.searchStructuredFact(loginUser, "phone", "customer-service-kb", 5))
                .thenReturn(structuredEvidence);

        AgentEvidenceBundle bundle = service.prepare(
                loginUser,
                RecentConversationContext.empty(),
                null,
                "UNIFIED_AGENT",
                "STRUCTURED_FACT_QUERY",
                "CONTACT_AND_CHANNEL",
                "STRUCTURED_FACT_QUERY",
                "STRUCTURED_FACT",
                null,
                "phone"
        );

        assertEquals("phone", bundle.effectiveQuestion());
        assertNotNull(bundle.directAnswer());
        assertTrue(bundle.directAnswer().contains("400-123-4567"));
        assertTrue(bundle.directAnswer().contains("contact-us"));
    }

    @Test
    void shouldForceDocumentEvidenceEscalationForDocumentProfile() {
        AgentEvidencePreparationServiceImpl service =
                new AgentEvidencePreparationServiceImpl(agentQuestionClassifier, knowledgeAgentFacade);
        LoginUser loginUser = LoginUser.builder().userId(1001L).build();

        when(agentQuestionClassifier.resolveKnowledgeBaseKeyword(eq("summary"), any())).thenReturn("lab-kb");
        when(agentQuestionClassifier.resolveDocumentKeyword(eq("summary"), any())).thenReturn("vision-lab-report");
        when(agentQuestionClassifier.isStructuredFactQuestion("summary")).thenReturn(false);
        when(knowledgeAgentFacade.searchKnowledge(loginUser, "summary", "lab-kb", 3)).thenReturn("MATCHED_COUNT: 0");
        when(knowledgeAgentFacade.searchDocumentContent(
                loginUser,
                "summary",
                "lab-kb",
                "vision-lab-report",
                8
        )).thenReturn("matched_snippet_count: 2");

        AgentEvidenceBundle bundle = service.prepare(
                loginUser,
                RecentConversationContext.builder().documentName("vision-lab-report").build(),
                null,
                "UNIFIED_AGENT",
                "DOCUMENT_CARRYOVER",
                "OTHER",
                "KNOWLEDGE_QA",
                "DOCUMENT_QA",
                "Stay inside the active upload.",
                "summary"
        );

        assertTrue(bundle.effectiveQuestion().contains("[Prefetched document evidence]"));
        assertTrue(bundle.effectiveQuestion().contains("[Execution profile]"));
        assertTrue(bundle.effectiveQuestion().contains("[Strategy execution directive]"));
    }
}
