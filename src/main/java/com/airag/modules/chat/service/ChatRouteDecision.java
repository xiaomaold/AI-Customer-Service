package com.airag.modules.chat.service;

import com.airag.modules.chat.enums.ChatRouteModeEnum;
import com.airag.modules.chat.routing.ActionRequestType;
import com.airag.modules.chat.routing.CustomerServiceDomain;
import com.airag.modules.chat.routing.CustomerServiceIntent;

public record ChatRouteDecision(ChatRouteModeEnum routeMode,
                                String routeReason,
                                CustomerServiceDomain serviceDomain,
                                CustomerServiceIntent serviceIntent,
                                ActionRequestType actionRequestType) {
}
