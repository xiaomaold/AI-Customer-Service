package com.airag.modules.admin.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminMissedQuestionDashboardVO {

    private Long totalCount;
    private Long openCount;
    private Long todayCount;
    private Long lastSevenDaysCount;
    private List<DimensionCountVO> routeModeCounts;
    private List<DimensionCountVO> missReasonCounts;
    private List<AdminMissedQuestionVO> recentMissedQuestions;

    @Data
    @Builder
    public static class DimensionCountVO {
        private String key;
        private Long count;
    }
}
