package com.airag.modules.agent.tools;

import cn.hutool.core.util.StrUtil;
import com.airag.modules.agent.context.RecentConversationContext;
import com.airag.modules.agent.service.KnowledgeAgentFacade;
import com.airag.modules.auth.security.LoginUser;
import dev.langchain4j.agent.tool.Tool;

public class KnowledgeAgentTools {

    private final KnowledgeAgentFacade knowledgeAgentFacade;
    private final LoginUser loginUser;
    private final RecentConversationContext context;

    public KnowledgeAgentTools(KnowledgeAgentFacade knowledgeAgentFacade, LoginUser loginUser, RecentConversationContext context) {
        this.knowledgeAgentFacade = knowledgeAgentFacade;
        this.loginUser = loginUser;
        this.context = context == null ? RecentConversationContext.empty() : context;
    }

    @Tool("""
            通用知识库查询工具。
            适用于：
            - 列出当前可查看的知识库
            - 按名称或描述模糊查找知识库
            - 查看知识库简介和文档数量
            参数说明：
            - keyword: 可为空，用于按知识库名称或描述做模糊匹配
            - limit: 可为空，默认 10，最大 20
            输出为结构化字段块。
            """)
    public String queryKnowledgeBases(String keyword, Integer limit) {
        return knowledgeAgentFacade.queryKnowledgeBases(loginUser, keyword, limit);
    }

    @Tool("""
            通用知识库文档查询工具。
            适用于：
            - 查询某个知识库下有几个文档
            - 查看某个知识库包含哪些文档
            - 按知识库名称和文档名称模糊匹配
            参数说明：
            - knowledgeBaseKeyword: 可为空，用于按知识库名称筛选
            - documentKeyword: 可为空，用于按文档名称或文件名筛选
            - limit: 可为空，默认 10，最大 20
            输出为结构化字段块。
            如果知识库没有匹配到，会自动回退到全局文档候选查询。
            """)
    public String queryKnowledgeDocuments(String knowledgeBaseKeyword, String documentKeyword, Integer limit) {
        String effectiveKnowledgeBaseKeyword = knowledgeBaseKeyword;
        String effectiveDocumentKeyword = documentKeyword;

        if (StrUtil.isBlank(effectiveKnowledgeBaseKeyword) && context.isApplyKnowledgeBaseCarryover()) {
            effectiveKnowledgeBaseKeyword = context.getKnowledgeBaseName();
        }
        if (StrUtil.isBlank(effectiveDocumentKeyword) && context.isApplyDocumentCarryover()) {
            effectiveDocumentKeyword = context.getDocumentName();
        }

        return knowledgeAgentFacade.queryKnowledgeDocuments(
                loginUser,
                effectiveKnowledgeBaseKeyword,
                effectiveDocumentKeyword,
                limit,
                shouldIncludeDocumentNames()
        );
    }

    @Tool("""
            通用知识内容搜索工具。
            适用于：
            - 回答知识内容问答
            - 在某个知识库或全部可访问知识库中检索相关片段
            参数说明：
            - question: 用户问题
            - knowledgeBaseKeyword: 可为空，用于先按知识库名称定位范围
            - topK: 可为空，默认 3，最大 5
            输出为结构化字段块。
            如果指定的知识库名称没有匹配到，会自动回退到全局内容搜索。
            """)
    public String searchKnowledge(String question, String knowledgeBaseKeyword, Integer topK) {
        String effectiveKnowledgeBaseKeyword = knowledgeBaseKeyword;
        String effectiveQuestion = question;

        if (StrUtil.isBlank(effectiveKnowledgeBaseKeyword) && context.isApplyKnowledgeBaseCarryover()) {
            effectiveKnowledgeBaseKeyword = context.getKnowledgeBaseName();
        }
        if (context.isApplyDocumentCarryover() && StrUtil.isNotBlank(context.getDocumentName()) && containsDocumentReference(question)) {
            effectiveQuestion = StrUtil.format("{}，当前追问文档：{}", StrUtil.blankToDefault(question, ""), context.getDocumentName());
        }

        return knowledgeAgentFacade.searchKnowledge(loginUser, effectiveQuestion, effectiveKnowledgeBaseKeyword, topK);
    }

    @Tool("""
            指定文档内容检索工具。
            适用于：
            - 用户在问某份文档写了什么、讲了什么、是谁的、包含哪些信息
            - 已经定位到某个知识库或某个文档后，继续查看该文档正文
            参数说明：
            - question: 用户问题
            - knowledgeBaseKeyword: 可为空，用于先按知识库名称缩小范围
            - documentKeyword: 可为空，用于按文档名称或文件名定位具体文档
            - topK: 可为空，默认 3，最大 5
            输出为结构化字段块。
            如果当前会话里已经定位到知识库或文档，可以留空让工具自动承接上下文。
            """)
    public String searchDocumentContent(String question, String knowledgeBaseKeyword, String documentKeyword, Integer topK) {
        String effectiveKnowledgeBaseKeyword = knowledgeBaseKeyword;
        String effectiveDocumentKeyword = documentKeyword;

        if (StrUtil.isBlank(effectiveKnowledgeBaseKeyword) && context.isApplyKnowledgeBaseCarryover()) {
            effectiveKnowledgeBaseKeyword = context.getKnowledgeBaseName();
        }
        if (StrUtil.isBlank(effectiveDocumentKeyword) && context.isApplyDocumentCarryover()) {
            effectiveDocumentKeyword = context.getDocumentName();
        }

        return knowledgeAgentFacade.searchDocumentContent(
                loginUser,
                question,
                effectiveKnowledgeBaseKeyword,
                effectiveDocumentKeyword,
                topK
        );
    }

    private boolean containsDocumentReference(String question) {
        if (StrUtil.isBlank(question)) {
            return false;
        }
        return question.contains("这个文档")
                || question.contains("该文档")
                || question.contains("这个文件")
                || question.contains("这份文档")
                || question.contains("里面")
                || question.contains("它");
    }

    private boolean shouldIncludeDocumentNames() {
        String question = context.getCurrentQuestion();
        if (StrUtil.isBlank(question)) {
            return true;
        }
        boolean asksCount = question.contains("几个文档")
                || question.contains("多少个文档")
                || question.contains("文档数量")
                || question.contains("共有几份")
                || question.contains("总共几份");
        boolean asksList = question.contains("哪些文档")
                || question.contains("有哪些文档")
                || question.contains("列出文档")
                || question.contains("文档列表")
                || question.contains("包括哪些");
        return !asksCount || asksList;
    }
}
