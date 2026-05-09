package com.airag.modules.chat.service.impl;

import cn.hutool.core.util.StrUtil;
import com.airag.modules.agent.service.AgentOrchestratorService;
import com.airag.modules.auth.security.LoginUser;
import com.airag.modules.chat.dto.ChatSendRequest;
import com.airag.modules.chat.entity.ChatMessage;
import com.airag.modules.chat.enums.ChatRouteModeEnum;
import com.airag.modules.chat.service.ChatMessageService;
import com.airag.modules.chat.service.ChatRouteDecider;
import com.airag.modules.chat.service.ChatRouteDecision;
import com.airag.modules.chat.service.ChatRoutingService;
import com.airag.modules.knowledge.service.KnowledgeBaseService;
import com.airag.modules.knowledge.vo.UserKnowledgePermissionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoutingServiceImpl implements ChatRoutingService {

    private final AgentOrchestratorService agentOrchestratorService;
    private final ChatMessageService chatMessageService;
    private final ChatRouteDecider chatRouteDecider;
    private final KnowledgeBaseService knowledgeBaseService;

    @Override
    public SseEmitter streamChat(ChatSendRequest request, LoginUser loginUser) {
        List<ChatMessage> recentMessages = chatMessageService.listRecentMessages(request.getSessionId(), 6);
        ChatRouteDecision decision = chatRouteDecider.decide(request, recentMessages);
        injectDefaultKnowledgeBaseIfNeeded(request, loginUser, decision);
        request.setRouteMode(decision.routeMode().name());
        request.setRouteReason(decision.routeReason());
        request.setRouteDomain(decision.serviceDomain().name());
        request.setRouteIntent(decision.serviceIntent().name());
        request.setRouteAction(decision.actionRequestType().name());

        log.info("Chat route mode={}, routeReason={}, routeDomain={}, routeIntent={}, routeAction={}, sessionId={}, knowledgeBaseId={}, userId={}, question={}",
                decision.routeMode(),
                decision.routeReason(),
                decision.serviceDomain(),
                decision.serviceIntent(),
                decision.actionRequestType(),
                request.getSessionId(),
                request.getKnowledgeBaseId(),
                request.getUserId(),
                StrUtil.blankToDefault(request.getQuestion(), ""));

        return agentOrchestratorService.streamChat(request, loginUser);
    }

    private void injectDefaultKnowledgeBaseIfNeeded(ChatSendRequest request,
                                                    LoginUser loginUser,
                                                    ChatRouteDecision decision) {
        if (request.getKnowledgeBaseId() != null
                || loginUser == null
                || !isOrdinaryUser(loginUser)
                || !shouldInjectDefaultKnowledgeBase(decision)) {
            return;
        }
        List<UserKnowledgePermissionVO> accessibleBases = knowledgeBaseService.listAccessibleByUserId(
                loginUser.getUserId(),
                loginUser.getRoles()
        );
        if (accessibleBases == null || accessibleBases.isEmpty()) {
            return;
        }
        UserKnowledgePermissionVO defaultKnowledgeBase = accessibleBases.get(0);
        if (defaultKnowledgeBase == null || defaultKnowledgeBase.getKnowledgeBaseId() == null) {
            return;
        }
        request.setKnowledgeBaseId(defaultKnowledgeBase.getKnowledgeBaseId());
    }

    private boolean shouldInjectDefaultKnowledgeBase(ChatRouteDecision decision) {
        return decision != null
                && (decision.routeMode() == ChatRouteModeEnum.UNIFIED_AGENT
                || decision.routeMode() == ChatRouteModeEnum.HYBRID_GENERATION);
    }

    private boolean isOrdinaryUser(LoginUser loginUser) {
        if (loginUser.getRoles() == null || loginUser.getRoles().isEmpty()) {
            return true;
        }
        return loginUser.getRoles().stream().noneMatch(role -> "ADMIN".equals(role) || "KB_ADMIN".equals(role));
    }
}
