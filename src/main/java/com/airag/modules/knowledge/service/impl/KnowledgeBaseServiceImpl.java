package com.airag.modules.knowledge.service.impl;

import cn.hutool.core.util.IdUtil;
import com.airag.common.exception.BusinessException;
import com.airag.modules.auth.entity.SysKbPermission;
import com.airag.modules.auth.mapper.SysKbPermissionMapper;
import com.airag.modules.knowledge.dto.CreateKnowledgeBaseRequest;
import com.airag.modules.knowledge.entity.KnowledgeBase;
import com.airag.modules.knowledge.entity.KnowledgeDocument;
import com.airag.modules.knowledge.mapper.KnowledgeBaseMapper;
import com.airag.modules.knowledge.mapper.KnowledgeDocumentMapper;
import com.airag.modules.knowledge.service.KnowledgeBaseService;
import com.airag.modules.knowledge.vo.KnowledgeBaseVO;
import com.airag.modules.knowledge.vo.UserKnowledgePermissionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final SysKbPermissionMapper sysKbPermissionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeBaseVO create(CreateKnowledgeBaseRequest request) {
        LocalDateTime now = LocalDateTime.now();
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        knowledgeBase.setId(IdUtil.getSnowflakeNextId());
        knowledgeBase.setKnowledgeBaseName(request.getKnowledgeBaseName());
        knowledgeBase.setDescription(request.getDescription());
        knowledgeBase.setStatus("ACTIVE");
        knowledgeBase.setDocumentCount(0);
        knowledgeBase.setCreateTime(now);
        knowledgeBase.setUpdateTime(now);
        knowledgeBase.setDeleted(0);
        knowledgeBaseMapper.insert(knowledgeBase);
        return toVO(knowledgeBase);
    }

    @Override
    public List<KnowledgeBaseVO> list(Long userId, List<String> roles) {
        if (hasManageRole(roles) || hasViewAllRole(roles)) {
            return knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                            .orderByDesc(KnowledgeBase::getCreateTime))
                    .stream()
                    .map(this::toVO)
                    .toList();
        }

        List<SysKbPermission> permissions = sysKbPermissionMapper.selectList(new LambdaQueryWrapper<SysKbPermission>()
                .eq(SysKbPermission::getUserId, userId));
        if (permissions.isEmpty()) {
            return List.of();
        }

        List<Long> knowledgeBaseIds = permissions.stream()
                .map(SysKbPermission::getKnowledgeBaseId)
                .distinct()
                .toList();
        return knowledgeBaseMapper.selectBatchIds(knowledgeBaseIds).stream()
                .filter(base -> base.getDeleted() == null || base.getDeleted() == 0)
                .map(this::toVO)
                .toList();
    }

    @Override
    public KnowledgeBaseVO getAccessibleDetail(Long userId, List<String> roles, Long knowledgeBaseId) {
        assertReadable(userId, roles, knowledgeBaseId);
        KnowledgeBase knowledgeBase = getExistingKnowledgeBase(knowledgeBaseId);
        return toVO(knowledgeBase);
    }

    @Override
    public List<UserKnowledgePermissionVO> listAccessibleByUserId(Long userId, List<String> roles) {
        if (hasManageRole(roles)) {
            return knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                            .orderByDesc(KnowledgeBase::getCreateTime))
                    .stream()
                    .map(base -> UserKnowledgePermissionVO.builder()
                            .knowledgeBaseId(base.getId())
                            .knowledgeBaseName(base.getKnowledgeBaseName())
                            .description(base.getDescription())
                            .permissionType("MANAGE")
                            .status(base.getStatus())
                            .build())
                    .toList();
        }
        if (hasViewAllRole(roles)) {
            return knowledgeBaseMapper.selectList(new LambdaQueryWrapper<KnowledgeBase>()
                            .orderByDesc(KnowledgeBase::getCreateTime))
                    .stream()
                    .map(base -> UserKnowledgePermissionVO.builder()
                            .knowledgeBaseId(base.getId())
                            .knowledgeBaseName(base.getKnowledgeBaseName())
                            .description(base.getDescription())
                            .permissionType("READ_ALL")
                            .status(base.getStatus())
                            .build())
                    .toList();
        }

        List<SysKbPermission> permissions = sysKbPermissionMapper.selectList(new LambdaQueryWrapper<SysKbPermission>()
                .eq(SysKbPermission::getUserId, userId));
        if (permissions.isEmpty()) {
            return List.of();
        }

        List<Long> knowledgeBaseIds = permissions.stream()
                .map(SysKbPermission::getKnowledgeBaseId)
                .distinct()
                .toList();
        Map<Long, KnowledgeBase> baseMap = knowledgeBaseMapper.selectBatchIds(knowledgeBaseIds).stream()
                .collect(Collectors.toMap(KnowledgeBase::getId, Function.identity()));

        return permissions.stream()
                .map(permission -> {
                    KnowledgeBase base = baseMap.get(permission.getKnowledgeBaseId());
                    if (base == null) {
                        return null;
                    }
                    return UserKnowledgePermissionVO.builder()
                            .knowledgeBaseId(base.getId())
                            .knowledgeBaseName(base.getKnowledgeBaseName())
                            .description(base.getDescription())
                            .permissionType(permission.getPermissionType())
                            .status(base.getStatus())
                            .build();
                })
                .filter(item -> item != null)
                .toList();
    }

    @Override
    public void assertReadable(Long userId, List<String> roles, Long knowledgeBaseId) {
        KnowledgeBase knowledgeBase = getExistingKnowledgeBase(knowledgeBaseId);
        if (hasManageRole(roles) || hasViewAllRole(roles)) {
            return;
        }

        Long count = sysKbPermissionMapper.selectCount(new LambdaQueryWrapper<SysKbPermission>()
                .eq(SysKbPermission::getUserId, userId)
                .eq(SysKbPermission::getKnowledgeBaseId, knowledgeBase.getId()));
        if (count == null || count <= 0) {
            throw new BusinessException("当前用户无权查看该知识库");
        }
    }

    @Override
    public void assertManageable(Long userId, List<String> roles, Long knowledgeBaseId) {
        KnowledgeBase knowledgeBase = getExistingKnowledgeBase(knowledgeBaseId);
        if (hasManageRole(roles)) {
            return;
        }

        Long count = sysKbPermissionMapper.selectCount(new LambdaQueryWrapper<SysKbPermission>()
                .eq(SysKbPermission::getUserId, userId)
                .eq(SysKbPermission::getKnowledgeBaseId, knowledgeBase.getId())
                .eq(SysKbPermission::getPermissionType, "WRITE"));
        if (count == null || count <= 0) {
            throw new BusinessException("当前用户无权管理该知识库");
        }
    }

    @Override
    public void delete(Long knowledgeBaseId) {
        assertExists(knowledgeBaseId);
        long count = knowledgeDocumentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId));
        if (count > 0) {
            throw new BusinessException("请先删除该知识库下的文档");
        }
        knowledgeBaseMapper.deleteById(knowledgeBaseId);
    }

    @Override
    public void assertExists(Long knowledgeBaseId) {
        getExistingKnowledgeBase(knowledgeBaseId);
    }

    @Override
    public void refreshDocumentCount(Long knowledgeBaseId) {
        Integer count = Math.toIntExact(knowledgeDocumentMapper.selectCount(new LambdaQueryWrapper<KnowledgeDocument>()
                .eq(KnowledgeDocument::getKnowledgeBaseId, knowledgeBaseId)));
        knowledgeBaseMapper.update(null, new LambdaUpdateWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getId, knowledgeBaseId)
                .set(KnowledgeBase::getDocumentCount, count)
                .set(KnowledgeBase::getUpdateTime, LocalDateTime.now()));
    }

    private boolean hasManageRole(List<String> roles) {
        return roles != null && roles.stream().anyMatch(role -> "KB_ADMIN".equals(role));
    }

    private boolean hasViewAllRole(List<String> roles) {
        return roles != null && roles.stream().anyMatch(role -> "ADMIN".equals(role));
    }

    private KnowledgeBase getExistingKnowledgeBase(Long knowledgeBaseId) {
        KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(knowledgeBaseId);
        if (knowledgeBase == null || (knowledgeBase.getDeleted() != null && knowledgeBase.getDeleted() == 1)) {
            throw new BusinessException("知识库不存在");
        }
        return knowledgeBase;
    }

    private KnowledgeBaseVO toVO(KnowledgeBase knowledgeBase) {
        return KnowledgeBaseVO.builder()
                .id(knowledgeBase.getId())
                .knowledgeBaseName(knowledgeBase.getKnowledgeBaseName())
                .description(knowledgeBase.getDescription())
                .status(knowledgeBase.getStatus())
                .documentCount(knowledgeBase.getDocumentCount())
                .createTime(knowledgeBase.getCreateTime())
                .build();
    }
}
