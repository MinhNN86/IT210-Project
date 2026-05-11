package com.projectit210.mapper;

import com.projectit210.entity.User;
import org.springframework.stereotype.Component;

/**
 * Mapper chuyển đổi giữa User entity và DTOs
 */
@Component
public class UserMapper {

    /**
     * Tạo bản sao đơn giản của thông tin user (không bao gồm password)
     */
    public static String getDisplayName(User user) {
        if (user == null) return "";
        return user.getFullName() != null ? user.getFullName() : user.getUsername();
    }
}
