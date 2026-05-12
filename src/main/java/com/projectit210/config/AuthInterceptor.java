package com.projectit210.config;

import com.projectit210.constant.AppConstant;
import com.projectit210.entity.User;
import com.projectit210.enums.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptor kiểm soát truy cập theo vai trò (CORE-02)
 * - /student/** → chỉ STUDENT
 * - /lecturer/** → chỉ LECTURER
 * - /admin/** → chỉ ADMIN
 * - /profile/** → bất kỳ user đã đăng nhập
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String path = request.getRequestURI();
        User currentUser = (User) request.getAttribute(AppConstant.CURRENT_USER);

        // Các trang công khai - không cần kiểm tra
        if (isPublicPath(path)) {
            return true;
        }

        // Kiểm tra đã đăng nhập chưa
        if (currentUser == null) {
            response.sendRedirect("/auth/login");
            return false;
        }

        // Kiểm tra quyền truy cập theo role
        Role userRole = currentUser.getRole();

        if (path.startsWith("/student/") && userRole != Role.STUDENT) {
            response.sendRedirect("/" + userRole.name().toLowerCase() + "/dashboard");
            return false;
        }

        if (path.startsWith("/lecturer/") && userRole != Role.LECTURER) {
            response.sendRedirect("/" + userRole.name().toLowerCase() + "/dashboard");
            return false;
        }

        if (path.startsWith("/admin/") && userRole != Role.ADMIN) {
            response.sendRedirect("/" + userRole.name().toLowerCase() + "/dashboard");
            return false;
        }

        if (path.startsWith("/meeting/") && userRole != Role.STUDENT && userRole != Role.LECTURER) {
            response.sendRedirect("/" + userRole.name().toLowerCase() + "/dashboard");
            return false;
        }

        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, org.springframework.web.servlet.ModelAndView modelAndView) throws Exception {
        if (modelAndView != null && !modelAndView.getViewName().startsWith("redirect:")) {
            modelAndView.addObject("requestURI", request.getRequestURI() != null ? request.getRequestURI() : "");
        }
    }

    private boolean isPublicPath(String path) {
        return path.equals("/") ||
               (path.startsWith("/auth/") && !path.startsWith("/auth/2fa-setup")) ||
               path.startsWith("/css/") ||
               path.startsWith("/js/") ||
               path.startsWith("/images/") ||
               path.startsWith("/webjars/") ||
               path.startsWith("/error") ||
               path.equals("/favicon.ico") ||
               path.startsWith("/api/"); // API endpoints cho AJAX calls
    }
}
