package com.projectit210.controller;

import com.projectit210.constant.AppConstant;
import com.projectit210.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller trang chủ - redirect về dashboard theo role
 */
@Controller
public class MentoringSessionController {

    @GetMapping("/")
    public String home(HttpServletRequest request) {
        User user = (User) request.getAttribute(AppConstant.CURRENT_USER);
        if (user == null) {
            return "redirect:/auth/login";
        }
        return switch (user.getRole()) {
            case ADMIN -> "redirect:/admin/dashboard";
            case LECTURER -> "redirect:/lecturer/dashboard";
            case STUDENT -> "redirect:/student/dashboard";
        };
    }

    @GetMapping("/error")
    public String errorPage() {
        return "error";
    }
}
