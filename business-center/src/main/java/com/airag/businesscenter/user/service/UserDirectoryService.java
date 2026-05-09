package com.airag.businesscenter.user.service;

import com.airag.businesscenter.common.BusinessException;
import com.airag.businesscenter.user.domain.BusinessUser;
import com.airag.businesscenter.user.domain.UserType;
import com.airag.businesscenter.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserDirectoryService {

    private final UserRepository userRepository;

    public UserDirectoryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public BusinessUser requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "user not found: " + userId));
    }

    public BusinessUser requireUser(Long userId, UserType expectedType) {
        BusinessUser user = requireUser(userId);
        if (user.userType() != expectedType) {
            throw new BusinessException("USER_TYPE_MISMATCH", "user type does not match required business rule");
        }
        return user;
    }

    public List<BusinessUser> listUsers() {
        return userRepository.findAll();
    }

    public long count() {
        return userRepository.count();
    }

    public void saveAll(List<BusinessUser> seedUsers) {
        for (BusinessUser user : seedUsers) {
            if (!userRepository.existsById(user.id())) {
                userRepository.insert(user);
            }
        }
    }
}
