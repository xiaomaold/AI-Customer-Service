package com.airag.modules.agent.service.impl;

import cn.hutool.core.util.StrUtil;
import com.airag.modules.agent.service.AgentAnswerPostProcessor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentAnswerPostProcessorImpl implements AgentAnswerPostProcessor {

    @Override
    public String finalizeAnswer(String originalQuestion, String effectiveQuestion, String answer) {
        if (looksLikeProgressOnlyAnswer(answer)) {
            return buildFallbackAnswer(originalQuestion, "");
        }
        String documentDiscoveryAnswer = buildDocumentDiscoveryAnswer(originalQuestion, effectiveQuestion);
        if (documentDiscoveryAnswer != null) {
            return documentDiscoveryAnswer;
        }
        return answer;
    }

    @Override
    public String buildFallbackAnswer(String question, String answer) {
        if (question != null && (question.contains("随便一个文档")
                || question.contains("任选一个文档")
                || question.contains("其中一个文档内容"))) {
            return "我需要先确认该知识库中的真实文档，再为你介绍其中一份文档的内容。你可以让我先列出文档，或指定某一个真实文档继续查看。";
        }
        if (question != null && (question.contains("退款") || question.contains("申请退款"))) {
            return "我刚才没能稳定拿到完整结果。你可以再试一次；如果知识库里有退款规则，我会基于检索到的内容直接整理给你。";
        }
        return StrUtil.blankToDefault(answer, "我目前只能基于已确认的工具结果给出保守回答。如需，我可以继续重新查询后再回答。");
    }

    @Override
    public String buildMissReason(String finalAnswer, String effectiveQuestion) {
        if (StrUtil.isBlank(finalAnswer)) {
            return "EMPTY_ANSWER";
        }
        if (finalAnswer.contains("当前未从知识库或工具结果中确认到该信息")) {
            return "NO_CONFIRMED_INFORMATION";
        }
        if (finalAnswer.contains("没有检索到相关知识片段")) {
            return "NO_KNOWLEDGE_CHUNKS";
        }
        if (finalAnswer.contains("没有找到匹配的知识库")) {
            return "NO_MATCHED_KNOWLEDGE_BASE";
        }
        if (finalAnswer.contains("没有找到可用于查看正文的匹配文档")) {
            return "NO_MATCHED_DOCUMENT";
        }
        if (finalAnswer.contains("我目前只能基于已确认的工具结果给出保守回答")) {
            return "SAFE_FALLBACK_ANSWER";
        }
        if (finalAnswer.contains("我刚才没能稳定拿到完整结果")) {
            return "UNSTABLE_RETRIEVAL";
        }
        if (StrUtil.isNotBlank(effectiveQuestion)
                && effectiveQuestion.contains("[Prefetched knowledge evidence]")
                && finalAnswer.contains("未确认")) {
            return "PREFETCHED_EVIDENCE_NOT_ENOUGH";
        }
        return null;
    }

    private boolean looksLikeProgressOnlyAnswer(String answer) {
        if (StrUtil.isBlank(answer)) {
            return false;
        }
        String normalized = answer.replace("…", "").replace("...", "").trim();
        return normalized.startsWith("正在为您查询")
                || normalized.startsWith("正在为你查询")
                || normalized.startsWith("正在查询")
                || normalized.startsWith("请稍候")
                || normalized.startsWith("请稍等")
                || normalized.startsWith("我来帮您查询")
                || normalized.startsWith("我来帮你查询")
                || normalized.startsWith("我先帮您查询")
                || normalized.startsWith("我先帮你查询");
    }

    private String buildDocumentDiscoveryAnswer(String originalQuestion, String effectiveQuestion) {
        if (!isBroadDocumentContentQuestion(originalQuestion) || hasPrefetchedDocumentEvidence(effectiveQuestion)) {
            return null;
        }

        List<String> sourceFiles = extractKnowledgeSourceFiles(effectiveQuestion);
        if (sourceFiles.size() <= 1) {
            return null;
        }
        return "我先检索到了 " + sourceFiles.size() + " 份相关文档：" + String.join("、", sourceFiles)
                + "。不过你现在的问题还比较泛，我还不能直接确认你想看哪一份的具体内容。你可以告诉我想查看哪份文档，或者把问题再问得更具体一点。";
    }

    private boolean isBroadDocumentContentQuestion(String question) {
        if (StrUtil.isBlank(question)) {
            return false;
        }
        return question.contains("内容")
                || question.contains("文档")
                || question.contains("文件")
                || question.contains("报告")
                || question.contains("资料");
    }

    private boolean hasPrefetchedDocumentEvidence(String effectiveQuestion) {
        return StrUtil.isNotBlank(effectiveQuestion)
                && effectiveQuestion.contains("[Prefetched document evidence]");
    }

    private List<String> extractKnowledgeSourceFiles(String effectiveQuestion) {
        if (StrUtil.isBlank(effectiveQuestion) || !effectiveQuestion.contains("[Prefetched knowledge evidence]")) {
            return List.of();
        }
        return effectiveQuestion.lines()
                .filter(line -> line.startsWith("source_file:"))
                .map(line -> line.substring("source_file:".length()).trim())
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
    }
}
