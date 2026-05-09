package com.airag.modules.admin.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.airag.modules.admin.entity.ChatMissedQuestion;
import com.airag.modules.admin.mapper.ChatMissedQuestionMapper;
import com.airag.modules.admin.service.MissedQuestionService;
import com.airag.modules.admin.vo.AdminMissedQuestionDashboardVO;
import com.airag.modules.admin.vo.AdminMissedQuestionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MissedQuestionServiceImpl implements MissedQuestionService {

    private final ChatMissedQuestionMapper chatMissedQuestionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordMissedQuestion(Long userId,
                                     Long sessionId,
                                     Long knowledgeBaseId,
                                     String routeMode,
                                     String question,
                                     String answer,
                                     String missReason) {
        if (userId == null || sessionId == null || StrUtil.isBlank(question) || StrUtil.isBlank(missReason)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        ChatMissedQuestion record = new ChatMissedQuestion();
        record.setId(IdUtil.getSnowflakeNextId());
        record.setUserId(userId);
        record.setSessionId(sessionId);
        record.setKnowledgeBaseId(knowledgeBaseId);
        record.setRouteMode(StrUtil.blankToDefault(routeMode, "UNKNOWN"));
        record.setQuestion(question.trim());
        record.setAnswer(StrUtil.blankToDefault(answer, ""));
        record.setMissReason(missReason.trim());
        record.setStatus("OPEN");
        record.setCreateTime(now);
        record.setUpdateTime(now);
        record.setDeleted(0);
        chatMissedQuestionMapper.insert(record);
    }

    @Override
    public List<AdminMissedQuestionVO> listMissedQuestions(String keyword, String routeMode, String status) {
        String normalizedKeyword = trimToNull(keyword);
        String normalizedRouteMode = trimToNull(routeMode);
        String normalizedStatus = trimToNull(status);
        return listAllRecords().stream()
                .filter(record -> matchesRecord(record, normalizedKeyword, normalizedRouteMode, normalizedStatus))
                .map(this::toVO)
                .toList();
    }

    @Override
    public AdminMissedQuestionDashboardVO getDashboard() {
        List<ChatMissedQuestion> records = listAllRecords();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfToday = now.toLocalDate().atStartOfDay();
        LocalDateTime sevenDaysAgo = now.minusDays(7);

        long totalCount = records.size();
        long openCount = records.stream().filter(record -> "OPEN".equalsIgnoreCase(record.getStatus())).count();
        long todayCount = records.stream().filter(record -> record.getCreateTime() != null && !record.getCreateTime().isBefore(startOfToday)).count();
        long lastSevenDaysCount = records.stream().filter(record -> record.getCreateTime() != null && !record.getCreateTime().isBefore(sevenDaysAgo)).count();

        return AdminMissedQuestionDashboardVO.builder()
                .totalCount(totalCount)
                .openCount(openCount)
                .todayCount(todayCount)
                .lastSevenDaysCount(lastSevenDaysCount)
                .routeModeCounts(buildDimensionCounts(records, ChatMissedQuestion::getRouteMode))
                .missReasonCounts(buildDimensionCounts(records, ChatMissedQuestion::getMissReason))
                .recentMissedQuestions(records.stream()
                        .sorted(Comparator.comparing(ChatMissedQuestion::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                        .limit(10)
                        .map(this::toVO)
                        .toList())
                .build();
    }

    private List<ChatMissedQuestion> listAllRecords() {
        return chatMissedQuestionMapper.selectList(new LambdaQueryWrapper<ChatMissedQuestion>()
                .orderByDesc(ChatMissedQuestion::getCreateTime));
    }

    private List<AdminMissedQuestionDashboardVO.DimensionCountVO> buildDimensionCounts(List<ChatMissedQuestion> records,
                                                                                       Function<ChatMissedQuestion, String> keyExtractor) {
        Map<String, Long> counts = records.stream()
                .collect(Collectors.groupingBy(
                        record -> StrUtil.blankToDefault(keyExtractor.apply(record), "UNKNOWN"),
                        Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> AdminMissedQuestionDashboardVO.DimensionCountVO.builder()
                        .key(entry.getKey())
                        .count(entry.getValue())
                        .build())
                .toList();
    }

    private boolean matchesRecord(ChatMissedQuestion record, String keyword, String routeMode, String status) {
        boolean matchesKeyword = keyword == null
                || containsIgnoreCase(record.getQuestion(), keyword)
                || containsIgnoreCase(record.getAnswer(), keyword)
                || containsIgnoreCase(record.getMissReason(), keyword);
        boolean matchesRouteMode = routeMode == null || routeMode.equalsIgnoreCase(record.getRouteMode());
        boolean matchesStatus = status == null || status.equalsIgnoreCase(record.getStatus());
        return matchesKeyword && matchesRouteMode && matchesStatus;
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword.toLowerCase());
    }

    private String trimToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private AdminMissedQuestionVO toVO(ChatMissedQuestion record) {
        return AdminMissedQuestionVO.builder()
                .missedQuestionId(record.getId())
                .userId(record.getUserId())
                .sessionId(record.getSessionId())
                .knowledgeBaseId(record.getKnowledgeBaseId())
                .routeMode(record.getRouteMode())
                .question(record.getQuestion())
                .answer(record.getAnswer())
                .missReason(record.getMissReason())
                .status(record.getStatus())
                .createTime(record.getCreateTime())
                .build();
    }
}
