package com.airag.businesscenter.user.domain;

import java.time.LocalDateTime;

public record BusinessUser(Long id,
                           String username,
                           String displayName,
                           UserType userType,
                           LocalDateTime createdTime,
                           LocalDateTime updatedTime) {
}
