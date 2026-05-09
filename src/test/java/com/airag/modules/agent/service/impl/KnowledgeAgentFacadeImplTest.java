package com.airag.modules.agent.service.impl;

import com.airag.modules.agent.query.impl.KnowledgeQueryPlannerImpl;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeAgentFacadeImplTest {

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private KnowledgeDocumentService knowledgeDocumentService;

    @Mock
    private KnowledgeRetrieverService knowledgeRetrieverService;

    private KnowledgeAgentFacadeImpl facade;
    private LoginUser loginUser;

    @BeforeEach
    void setUp() {
        DefaultQuestionIntentFeatureExtractor featureExtractor = new DefaultQuestionIntentFeatureExtractor(
                new DefaultSentencePatternAnalyzer(),
                new DefaultBusinessSignalAnalyzer(),
                new DefaultEnterpriseNeedClassifier(),
                new DefaultRouteRuleEngine()
        );
        facade = new KnowledgeAgentFacadeImpl(
                knowledgeBaseService,
                knowledgeDocumentService,
                knowledgeRetrieverService,
                new KnowledgeQueryPlannerImpl(featureExtractor),
                featureExtractor
        );
        loginUser = LoginUser.builder()
                .userId(1L)
                .username("tester")
                .roles(List.of("USER"))
                .build();
    }

    @Test
    void searchKnowledge_shouldUseAccessibleKnowledgeBaseIdsForGlobalSearch() {
        when(knowledgeBaseService.list(1L, List.of("USER"))).thenReturn(List.of(
                base(11L, "知识库A"),
                base(22L, "知识库B")
        ));
        when(knowledgeRetrieverService.retrieve(eq("实验报告内容"), anyInt(), eq(List.of(11L, 22L))))
                .thenReturn(List.of(new KnowledgeRetrieverService.RetrievedChunk(
                        "chunk-1", 11L, 101L, 0, "实验报告.pdf", "实验报告摘要", 0.91
                )));

        String result = facade.searchKnowledge(loginUser, "实验报告内容", null, 3);

        verify(knowledgeRetrieverService).retrieve(eq("实验报告内容"), anyInt(), eq(List.of(11L, 22L)));
        assertTrue(result.contains("MATCHED_COUNT: 1"));
        assertTrue(result.contains("source_file: 实验报告.pdf"));
    }

    @Test
    void searchKnowledge_shouldExpandRefundRuleQueries() {
        when(knowledgeBaseService.list(1L, List.of("USER"))).thenReturn(List.of(
                base(11L, "企业客服知识库")
        ));
        when(knowledgeRetrieverService.retrieve(eq("退款规则"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("退款规则是什么"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("退款规则有哪些"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("退款"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("退款政策"), anyInt(), eq(List.of(11L))))
                .thenReturn(List.of(new KnowledgeRetrieverService.RetrievedChunk(
                        "chunk-refund", 11L, 201L, 0, "退款与售后政策.pdf", "购买后7天内且未开通正式服务，可申请全额退款。", 0.94
                )));
        when(knowledgeRetrieverService.retrieve(eq("退款与售后政策"), anyInt(), eq(List.of(11L))))
                .thenReturn(List.of(new KnowledgeRetrieverService.RetrievedChunk(
                        "chunk-policy", 11L, 201L, 1, "退款与售后政策.pdf", "审核通过后原路退款一般在5至10个工作日内到账。", 0.93
                )));
        when(knowledgeRetrieverService.retrieve(eq("退款流程"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("退款制度"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("退款说明"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("售后政策"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("退款申请方式"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("审核与到账时效"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());

        String result = facade.searchKnowledge(loginUser, "退款规则", null, 3);

        verify(knowledgeRetrieverService).retrieve(eq("退款政策"), anyInt(), eq(List.of(11L)));
        verify(knowledgeRetrieverService).retrieve(eq("退款与售后政策"), anyInt(), eq(List.of(11L)));
        assertTrue(result.contains("MATCHED_COUNT: 2"));
        assertTrue(result.contains("source_file: 退款与售后政策.pdf"));
    }

    @Test
    void searchKnowledge_shouldExpandGenerationQuestionIntoBusinessTopicQueries() {
        when(knowledgeBaseService.list(1L, List.of("USER"))).thenReturn(List.of(
                base(11L, "企业客服知识库")
        ));
        when(knowledgeRetrieverService.retrieve(eq("帮我生成一个退款申请表"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("退款"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("退款规则"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("退款流程"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("退款制度"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("退款说明"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("退款政策"), anyInt(), eq(List.of(11L))))
                .thenReturn(List.of(new KnowledgeRetrieverService.RetrievedChunk(
                        "chunk-refund-form", 11L, 301L, 0, "退款与售后政策.pdf", "发送邮件至 refund@nebula-ai.com 提交退款申请。", 0.95
                )));
        when(knowledgeRetrieverService.retrieve(eq("退款与售后政策"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("售后政策"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("退款申请方式"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("审核与到账时效"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());

        String result = facade.searchKnowledge(loginUser, "帮我生成一个退款申请表", null, 3);

        verify(knowledgeRetrieverService).retrieve(eq("退款政策"), anyInt(), eq(List.of(11L)));
        verify(knowledgeRetrieverService).retrieve(eq("退款规则"), anyInt(), eq(List.of(11L)));
        assertTrue(result.contains("MATCHED_COUNT: 1"));
        assertTrue(result.contains("source_file: 退款与售后政策.pdf"));
    }

    @Test
    void searchKnowledge_shouldPreferBusinessAliasOverLeadingDescription() {
        when(knowledgeBaseService.list(1L, List.of("USER"))).thenReturn(List.of(
                base(11L, "企业人事知识库")
        ));
        when(knowledgeRetrieverService.retrieve(eq("我生病了，帮我写一个请假申请表"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("请假"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("请假规则"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("请假流程"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("请假制度"), anyInt(), eq(List.of(11L))))
                .thenReturn(List.of(new KnowledgeRetrieverService.RetrievedChunk(
                        "chunk-leave", 11L, 401L, 0, "请假制度.pdf", "病假需提交请假申请并附病历。", 0.96
                )));
        when(knowledgeRetrieverService.retrieve(eq("请假政策"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("请假说明"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("请假规定"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("病假规定"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());
        when(knowledgeRetrieverService.retrieve(eq("病假流程"), anyInt(), eq(List.of(11L)))).thenReturn(List.of());

        String result = facade.searchKnowledge(loginUser, "我生病了，帮我写一个请假申请表", null, 3);

        verify(knowledgeRetrieverService).retrieve(eq("请假制度"), anyInt(), eq(List.of(11L)));
        assertTrue(result.contains("MATCHED_COUNT: 1"));
        assertTrue(result.contains("source_file: 请假制度.pdf"));
    }

    @Test
    void searchStructuredFact_shouldExtractPhoneFromContactDocument() {
        when(knowledgeBaseService.list(1L, List.of("USER"))).thenReturn(List.of(
                base(11L, "企业客服知识库")
        ));
        when(knowledgeDocumentService.listDocuments(1L, List.of("USER"), 11L)).thenReturn(List.of(
                document(101L, 11L, "联系我们", "联系我们.md")
        ));
        when(knowledgeRetrieverService.retrieve(eq("围绕文档《联系我们》回答：客服电话是什么"), anyInt(), eq(11L)))
                .thenReturn(List.of(new KnowledgeRetrieverService.RetrievedChunk(
                        "chunk-2", 11L, 101L, 0, "联系我们.md", "客服电话：400-800-1234，邮箱：refund@nebula-ai.com", 0.95
                )));
        when(knowledgeRetrieverService.retrieve(eq("文档《联系我们》中的联系电话"), anyInt(), eq(11L)))
                .thenReturn(List.of(new KnowledgeRetrieverService.RetrievedChunk(
                        "chunk-3", 11L, 101L, 0, "联系我们.md", "客服电话：400-800-1234，邮箱：refund@nebula-ai.com", 0.96
                )));

        String result = facade.searchStructuredFact(loginUser, "客服电话是什么", null, 5);

        assertTrue(result.contains("RESULT_TYPE: STRUCTURED_FACT_SEARCH"));
        assertTrue(result.contains("MATCHED_COUNT: 1"));
        assertTrue(result.contains("extracted_value: 400-800-1234"));
    }

    private KnowledgeBaseVO base(Long id, String name) {
        return KnowledgeBaseVO.builder()
                .id(id)
                .knowledgeBaseName(name)
                .documentCount(1)
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
