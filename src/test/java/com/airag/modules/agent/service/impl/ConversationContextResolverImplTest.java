package com.airag.modules.agent.service.impl;

import com.airag.modules.agent.context.RecentConversationContext;
import com.airag.modules.auth.security.LoginUser;
import com.airag.modules.chat.entity.ChatMessage;
import com.airag.modules.chat.enums.MessageRoleEnum;
import com.airag.modules.knowledge.service.KnowledgeBaseService;
import com.airag.modules.knowledge.service.KnowledgeDocumentService;
import com.airag.modules.knowledge.vo.KnowledgeBaseVO;
import com.airag.modules.knowledge.vo.KnowledgeDocumentVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationContextResolverImplTest {

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private KnowledgeDocumentService knowledgeDocumentService;

    private ConversationContextResolverImpl resolver;
    private LoginUser loginUser;

    @BeforeEach
    void setUp() {
        resolver = new ConversationContextResolverImpl(knowledgeBaseService, knowledgeDocumentService);
        loginUser = LoginUser.builder()
                .userId(1001L)
                .username("admin")
                .roles(List.of("ADMIN"))
                .build();
    }

    @Test
    void resolve_shouldCarryKnowledgeBaseFromRecentConversation() {
        mockAccessibleKnowledge();

        RecentConversationContext context = resolver.resolve(
                loginUser,
                1L,
                null,
                List.of(
                        message(101L, MessageRoleEnum.USER.getCode(), "简历库有几个文档"),
                        message(102L, MessageRoleEnum.ASSISTANT.getCode(), "简历库当前共有 1 个文档")
                ),
                "这个库里有哪些文档",
                null,
                null
        );

        assertTrue(context.isApplyKnowledgeBaseCarryover());
        assertEquals("简历库", context.getKnowledgeBaseName());
        assertFalse(context.isExplicitKnowledgeBaseInQuestion());
    }

    @Test
    void resolve_shouldCarryDocumentFromRecentConversation() {
        mockAccessibleKnowledge();

        RecentConversationContext context = resolver.resolve(
                loginUser,
                1L,
                null,
                List.of(
                        message(101L, MessageRoleEnum.USER.getCode(), "简历库里有简历模板吗"),
                        message(102L, MessageRoleEnum.ASSISTANT.getCode(), "简历库中有文档《简历模板》")
                ),
                "这个文档是什么内容",
                null,
                null
        );

        assertTrue(context.isApplyDocumentCarryover());
        assertEquals("简历模板", context.getDocumentName());
        assertEquals("简历库", context.getKnowledgeBaseName());
    }

    @Test
    void resolve_shouldRespectExplicitKnowledgeBaseOverride() {
        mockAccessibleKnowledge();

        RecentConversationContext context = resolver.resolve(
                loginUser,
                1L,
                null,
                List.of(message(101L, MessageRoleEnum.USER.getCode(), "简历库有几个文档")),
                "企业客服知识库有几个文档",
                null,
                null
        );

        assertTrue(context.isExplicitKnowledgeBaseInQuestion());
        assertEquals("企业客服知识库", context.getKnowledgeBaseName());
        assertFalse(context.isApplyKnowledgeBaseCarryover());
    }

    @Test
    void resolve_shouldKeepQuestionWithoutCarryoverWhenNoReferenceExists() {
        mockAccessibleKnowledge();

        RecentConversationContext context = resolver.resolve(
                loginUser,
                1L,
                null,
                List.of(message(101L, MessageRoleEnum.USER.getCode(), "简历库有几个文档")),
                "请假流程是什么",
                null,
                null
        );

        assertFalse(context.hasCarryover());
        assertEquals(null, context.getKnowledgeBaseName());
    }

    @Test
    void resolve_shouldPreferFrontendProvidedDocumentCarryover() {
        mockAccessibleKnowledge();

        RecentConversationContext context = resolver.resolve(
                loginUser,
                1L,
                null,
                List.of(
                        message(101L, MessageRoleEnum.USER.getCode(), "北京在哪"),
                        message(102L, MessageRoleEnum.ASSISTANT.getCode(), "北京是中国首都")
                ),
                "主要内容是什么",
                "实验报告库",
                "图像的几何运算实验报告"
        );

        assertTrue(context.isApplyDocumentCarryover());
        assertTrue(context.isApplyKnowledgeBaseCarryover());
        assertEquals("实验报告库", context.getKnowledgeBaseName());
        assertEquals("图像的几何运算实验报告", context.getDocumentName());
    }

    private void mockAccessibleKnowledge() {
        when(knowledgeBaseService.list(eq(1001L), eq(List.of("ADMIN")))).thenReturn(List.of(
                base(1L, "简历库"),
                base(2L, "企业客服知识库"),
                base(3L, "实验报告库")
        ));
        when(knowledgeDocumentService.listDocuments(eq(1001L), eq(List.of("ADMIN")), eq(1L))).thenReturn(List.of(
                document(1L, "简历模板", "简历模板.pdf")
        ));
        when(knowledgeDocumentService.listDocuments(eq(1001L), eq(List.of("ADMIN")), eq(2L))).thenReturn(List.of(
                document(2L, "客服手册", "客服手册.pdf")
        ));
        when(knowledgeDocumentService.listDocuments(eq(1001L), eq(List.of("ADMIN")), eq(3L))).thenReturn(List.of(
                document(3L, "图像的几何运算实验报告", "图像的几何运算实验报告.pdf")
        ));
    }

    private ChatMessage message(Long id, String role, String content) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setId(id);
        chatMessage.setRole(role);
        chatMessage.setContent(content);
        chatMessage.setCreateTime(LocalDateTime.now());
        return chatMessage;
    }

    private KnowledgeBaseVO base(Long id, String name) {
        return KnowledgeBaseVO.builder()
                .id(id)
                .knowledgeBaseName(name)
                .documentCount(1)
                .build();
    }

    private KnowledgeDocumentVO document(Long knowledgeBaseId, String documentName, String fileName) {
        return KnowledgeDocumentVO.builder()
                .knowledgeBaseId(knowledgeBaseId)
                .documentName(documentName)
                .fileName(fileName)
                .build();
    }
}
