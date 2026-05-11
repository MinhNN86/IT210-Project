package com.projectit210.controller;

import com.projectit210.dto.request.LoginRequest;
import com.projectit210.dto.request.RegisterRequest;
import com.projectit210.entity.User;
import com.projectit210.exception.BadRequestException;
import com.projectit210.security.JwtUtil;
import com.projectit210.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtUtil jwtUtil;

    @Value("${app.jwt.cookie-name}")
    private String cookieName;

    @GetMapping("/login")
    public String loginPage(Model model) {
        model.addAttribute("loginRequest", new LoginRequest());
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute LoginRequest loginRequest,
                        BindingResult bindingResult,
                        HttpServletResponse response,
                        Model model,
                        RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "auth/login";
        }
        try {
            User user = authService.login(loginRequest);
            String token = jwtUtil.generateToken(user.getId());

            Cookie cookie = new Cookie(cookieName, token);
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge(86400); // 24h
            response.addCookie(cookie);

            // Redirect theo role
            return switch (user.getRole()) {
                case ADMIN -> "redirect:/admin/dashboard";
                case LECTURER -> "redirect:/lecturer/dashboard";
                case STUDENT -> "redirect:/student/dashboard";
            };
        } catch (BadRequestException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "auth/login";
        }
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        RegisterRequest req = new RegisterRequest();
        req.setRole("STUDENT"); // Mặc định chỉ cho đăng ký sinh viên
        model.addAttribute("registerRequest", req);
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute RegisterRequest registerRequest,
                           BindingResult bindingResult,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        // Buộc role là STUDENT bất kể client gửi gì
        registerRequest.setRole("STUDENT");
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }
        try {
            authService.register(registerRequest);
            redirectAttributes.addFlashAttribute("successMessage", "Đăng ký thành công! Vui lòng đăng nhập.");
            return "redirect:/auth/login";
        } catch (BadRequestException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "auth/register";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpServletResponse response) {
        Cookie cookie = new Cookie(cookieName, null);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return "redirect:/auth/login";
    }
}
