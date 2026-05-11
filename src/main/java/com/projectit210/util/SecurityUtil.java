package com.projectit210.util;

import com.projectit210.constant.AppConstant;
import com.projectit210.entity.User;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Tiện ích lấy thông tin user hiện tại từ request attribute (được set bởi JwtFilter)
 */
public final class SecurityUtil {

    private SecurityUtil() {}

    /**
     * Lấy user hiện tại từ request attribute
     */
    public static User getCurrentUser(HttpServletRequest request) {
        return (User) request.getAttribute(AppConstant.CURRENT_USER);
    }

    /**
     * Lấy user ID hiện tại từ request attribute
     */
    public static String getCurrentUserId(HttpServletRequest request) {
        return (String) request.getAttribute(AppConstant.CURRENT_USER_ID);
    }
}
