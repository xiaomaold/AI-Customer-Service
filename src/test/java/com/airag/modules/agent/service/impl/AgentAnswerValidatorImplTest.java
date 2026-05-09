package com.airag.modules.agent.service.impl;

import com.airag.modules.agent.service.AgentAnswerValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAnswerValidatorImplTest {

    private AgentAnswerValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AgentAnswerValidatorImpl();
    }

    @Test
    void validate_shouldRewriteCountOnlyAnswerWhenItContainsList() {
        AgentAnswerValidator.ValidationResult result = validator.validate(
                "企业客服知识库有几个文档",
                "该知识库中目前共有 7 个文档，包括：\n-《客服服务标准 SOP》\n-《常见客户问题应答手册》"
        );

        assertTrue(result.valid());
        assertEquals("该知识库当前共有 7 个文档。", result.answer());
    }

    @Test
    void validate_shouldTriggerRetryWhenContentRequestLeaksInternalProtocol() {
        AgentAnswerValidator.ValidationResult result = validator.validate(
                "企业客服知识库其中随便一个文档内容给我说一下",
                "当前系统无法直接获取具体内容，DOCUMENT_NAMES_INCLUDED=false，且无内容字段返回。"
        );

        assertFalse(result.valid());
        assertTrue(result.needsRetry());
    }

    @Test
    void validate_shouldTriggerRetryForEnterpriseFactHallucination() {
        AgentAnswerValidator.ValidationResult result = validator.validate(
                "公司叫什么",
                "公司名称是“杭州智谷科技有限公司”。"
        );

        assertFalse(result.valid());
        assertTrue(result.needsRetry());
    }

    @Test
    void validate_shouldKeepConservativeEnterpriseFactAnswer() {
        AgentAnswerValidator.ValidationResult result = validator.validate(
                "公司叫什么",
                "当前未从知识库或工具结果中确认到该信息。"
        );

        assertTrue(result.valid());
        assertFalse(result.needsRetry());
    }

    @Test
    void validate_shouldRemoveInternalFieldLines() {
        AgentAnswerValidator.ValidationResult result = validator.validate(
                "请假流程是什么",
                "RESULT_TYPE: KNOWLEDGE_SEARCH\n请假流程需要审批后生效。\nMATCHED_COUNT: 1"
        );

        assertTrue(result.valid());
        assertEquals("请假流程需要审批后生效。", result.answer());
    }
}
