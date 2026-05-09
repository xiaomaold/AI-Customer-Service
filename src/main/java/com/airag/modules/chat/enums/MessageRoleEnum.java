package com.airag.modules.chat.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MessageRoleEnum {

    USER("user"),
    ASSISTANT("assistant");

    private final String code;
}
