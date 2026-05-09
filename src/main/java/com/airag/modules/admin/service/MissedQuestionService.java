package com.airag.modules.admin.service;

import com.airag.modules.admin.vo.AdminMissedQuestionVO;
import com.airag.modules.admin.vo.AdminMissedQuestionDashboardVO;

import java.util.List;

public interface MissedQuestionService {

    void recordMissedQuestion(Long userId,
                              Long sessionId,
                              Long knowledgeBaseId,
                              String routeMode,
                              String question,
                              String answer,
                              String missReason);

    List<AdminMissedQuestionVO> listMissedQuestions(String keyword, String routeMode, String status);

    AdminMissedQuestionDashboardVO getDashboard();
}
