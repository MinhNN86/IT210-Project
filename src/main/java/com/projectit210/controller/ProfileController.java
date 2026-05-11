package com.projectit210.controller;

import com.projectit210.constant.AppConstant;
import com.projectit210.dto.request.ProfileUpdateRequest;
import com.projectit210.entity.User;
import com.projectit210.exception.ConflictException;
import com.projectit210.service.ProfileService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public String profilePage(HttpServletRequest request, Model model) {
        User user = (User) request.getAttribute(AppConstant.CURRENT_USER);
        model.addAttribute("user", profileService.getProfile(user.getId()));
        return user.getRole().name().toLowerCase() + "/profile";
    }

    @PostMapping
    public String updateProfile(@ModelAttribute ProfileUpdateRequest profileUpdateRequest,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        User user = (User) request.getAttribute(AppConstant.CURRENT_USER);
        try {
            profileService.updateProfile(user.getId(), profileUpdateRequest);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật hồ sơ thành công!");
        } catch (ConflictException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/profile";
    }
}
