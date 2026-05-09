package com.airag.modules.knowledge.service;

import com.airag.modules.knowledge.dto.GrantKnowledgePermissionRequest;
import com.airag.modules.knowledge.vo.KnowledgePermissionGrantVO;
import com.airag.modules.knowledge.vo.KnowledgePermissionUserVO;

import java.util.List;

public interface KnowledgePermissionService {

    List<KnowledgePermissionUserVO> listAssignableUsers();

    List<KnowledgePermissionGrantVO> listGrants(Long knowledgeBaseId);

    void grant(Long knowledgeBaseId, GrantKnowledgePermissionRequest request);

    void revoke(Long knowledgeBaseId, Long userId);
}
