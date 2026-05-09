package com.airag.modules.knowledge.service;

import com.airag.modules.knowledge.dto.CreateKnowledgeBaseRequest;
import com.airag.modules.knowledge.vo.KnowledgeBaseVO;
import com.airag.modules.knowledge.vo.UserKnowledgePermissionVO;

import java.util.List;

public interface KnowledgeBaseService {

    KnowledgeBaseVO create(CreateKnowledgeBaseRequest request);

    List<KnowledgeBaseVO> list(Long userId, List<String> roles);

    KnowledgeBaseVO getAccessibleDetail(Long userId, List<String> roles, Long knowledgeBaseId);

    List<UserKnowledgePermissionVO> listAccessibleByUserId(Long userId, List<String> roles);

    void assertReadable(Long userId, List<String> roles, Long knowledgeBaseId);

    void assertManageable(Long userId, List<String> roles, Long knowledgeBaseId);

    void delete(Long knowledgeBaseId);

    void assertExists(Long knowledgeBaseId);

    void refreshDocumentCount(Long knowledgeBaseId);
}
