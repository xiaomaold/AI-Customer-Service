package com.airag.modules.agent.tools;

import com.airag.modules.agent.context.RecentConversationContext;
import com.airag.modules.agent.query.impl.KnowledgeQueryPlannerImpl;
import com.airag.modules.agent.service.impl.KnowledgeAgentFacadeImpl;
import com.airag.modules.auth.security.LoginUser;
import com.airag.modules.chat.routing.impl.DefaultBusinessSignalAnalyzer;
import com.airag.modules.chat.routing.impl.DefaultEnterpriseNeedClassifier;
import com.airag.modules.chat.routing.impl.DefaultQuestionIntentFeatureExtractor;
import com.airag.modules.chat.routing.impl.DefaultRouteRuleEngine;
import com.airag.modules.chat.routing.impl.DefaultSentencePatternAnalyzer;
import com.airag.modules.chat.service.KnowledgeRetrieverService;
import com.airag.modules.knowledge.service.KnowledgeBaseService;
import com.airag.modules.knowledge.service.KnowledgeDocumentService;
import com.airag.modules.knowledge.vo.KnowledgeBaseVO;
import com.airag.modules.knowledge.vo.KnowledgeDocumentVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeAgentToolsTest {

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private KnowledgeDocumentService knowledgeDocumentService;

    @Mock
    private KnowledgeRetrieverService knowledgeRetrieverService;

    private LoginUser loginUser;
    private KnowledgeAgentFacadeImpl knowledgeAgentFacade;

    @BeforeEach
    void setUp() {
        loginUser = LoginUser.builder()
                .userId(1001L)
                .username("tester")
                .nickname("tester")
                .roles(List.of("ADMIN"))
                .build();
        DefaultQuestionIntentFeatureExtractor featureExtractor = new DefaultQuestionIntentFeatureExtractor(
                new DefaultSentencePatternAnalyzer(),
                new DefaultBusinessSignalAnalyzer(),
                new DefaultEnterpriseNeedClassifier(),
                new DefaultRouteRuleEngine()
        );
        knowledgeAgentFacade = new KnowledgeAgentFacadeImpl(
                knowledgeBaseService,
                knowledgeDocumentService,
                knowledgeRetrieverService,
                new KnowledgeQueryPlannerImpl(featureExtractor),
                featureExtractor
        );
    }

    @Test
    void queryKnowledgeBases_shouldReturnStructuredKnowledgeBaseList() {
        KnowledgeAgentTools knowledgeAgentTools = createTools(RecentConversationContext.empty());
        when(knowledgeBaseService.list(eq(1001L), eq(List.of("ADMIN")))).thenReturn(List.of(
                base(1L, "简历库", "个人简历文档", 1),
                base(2L, "企业客服知识库", "客服 FAQ", 8)
        ));

        String result = knowledgeAgentTools.queryKnowledgeBases(null, 10);

        assertTrue(result.contains("RESULT_TYPE: KNOWLEDGE_BASES"));
        assertTrue(result.contains("MATCHED_COUNT: 2"));
        assertTrue(result.contains("name: 简历库"));
    }

    @Test
    void queryKnowledgeDocuments_shouldHideDocumentNamesForCountOnlyQuestion() {
        KnowledgeAgentTools knowledgeAgentTools = createTools(RecentConversationContext.builder()
                .currentQuestion("企业客服知识库有几个文档")
                .build());
        when(knowledgeBaseService.list(eq(1001L), eq(List.of("ADMIN")))).thenReturn(List.of(
                base(2L, "企业客服知识库", "客服 FAQ", 7)
        ));
        when(knowledgeDocumentService.listDocuments(eq(1001L), eq(List.of("ADMIN")), eq(2L))).thenReturn(List.of(
                document(201L, 2L, "客服服务标准 SOP", "客服服务标准SOP.pdf"),
                document(202L, 2L, "投诉处理指引", "投诉处理指引.pdf")
        ));

        String result = knowledgeAgentTools.queryKnowledgeDocuments("企业客服知识库", null, 10);

        assertTrue(result.contains("DOCUMENT_NAMES_INCLUDED: false"));
        assertTrue(result.contains("knowledge_base_document_count: 7"));
        assertFalse(result.contains("document_1_name:"));
    }

    @Test
    void searchKnowledge_shouldEnhanceQuestionWithDocumentCarryover() {
        KnowledgeAgentTools knowledgeAgentTools = createTools(RecentConversationContext.builder()
                .currentQuestion("这个文档是什么内容")
                .knowledgeBaseName("简历库")
                .documentName("张三简历")
                .applyKnowledgeBaseCarryover(true)
                .applyDocumentCarryover(true)
                .build());
        when(knowledgeBaseService.list(eq(1001L), eq(List.of("ADMIN")))).thenReturn(List.of(
                base(1L, "简历库", "个人简历文档", 1)
        ));
        when(knowledgeRetrieverService.retrieve(eq("这个文档是什么内容，当前追问文档：张三简历"), anyInt(), eq(1L))).thenReturn(List.of(
                new KnowledgeRetrieverService.RetrievedChunk("c1", 1L, 101L, 0, "张三简历.pdf", "包含个人基本信息和项目经历。", 0.91)
        ));

        String result = knowledgeAgentTools.searchKnowledge("这个文档是什么内容", null, 3);

        assertTrue(result.contains("SEARCH_SCOPE: MATCHED_KNOWLEDGE_BASE"));
    }

    @Test
    void searchDocumentContent_shouldReadSpecificDocumentContent() {
        KnowledgeAgentTools knowledgeAgentTools = createTools(RecentConversationContext.builder()
                .currentQuestion("简历是谁的")
                .build());
        when(knowledgeBaseService.list(eq(1001L), eq(List.of("ADMIN")))).thenReturn(List.of(
                base(1L, "简历库", "个人简历", 1)
        ));
        when(knowledgeDocumentService.listDocuments(eq(1001L), eq(List.of("ADMIN")), eq(1L))).thenReturn(List.of(
                document(101L, 1L, "张三简历", "张三简历.pdf")
        ));
        when(knowledgeRetrieverService.retrieve(eq("围绕文档《张三简历》回答：简历是谁的"), anyInt(), eq(1L))).thenReturn(List.of(
                new KnowledgeRetrieverService.RetrievedChunk("c1", 1L, 101L, 0, "张三简历.pdf", "姓名：张三，电话：13800000000。", 0.95),
                new KnowledgeRetrieverService.RetrievedChunk("c2", 1L, 101L, 1, "张三简历.pdf", "工作经历：三年 Java 开发经验。", 0.88)
        ));

        String result = knowledgeAgentTools.searchDocumentContent("简历是谁的", "简历库", null, 3);

        assertTrue(result.contains("RESULT_TYPE: DOCUMENT_CONTENT_SEARCH"));
        assertTrue(result.contains("document_name: 张三简历"));
        assertTrue(result.contains("snippet: 姓名：张三"));
    }

    private KnowledgeAgentTools createTools(RecentConversationContext context) {
        return new KnowledgeAgentTools(knowledgeAgentFacade, loginUser, context);
    }

    private KnowledgeBaseVO base(Long id, String name, String description, Integer documentCount) {
        return KnowledgeBaseVO.builder()
                .id(id)
                .knowledgeBaseName(name)
                .description(description)
                .documentCount(documentCount)
                .build();
    }

    private KnowledgeDocumentVO document(Long id, Long knowledgeBaseId, String documentName, String fileName) {
        return KnowledgeDocumentVO.builder()
                .id(id)
                .knowledgeBaseId(knowledgeBaseId)
                .documentName(documentName)
                .fileName(fileName)
                .build();
    }
}
