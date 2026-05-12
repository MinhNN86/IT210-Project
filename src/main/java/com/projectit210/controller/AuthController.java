package com.projectit210.controller;

import com.projectit210.constant.AppConstant;
import com.projectit210.dto.request.LoginRequest;
import com.projectit210.dto.request.RegisterRequest;
import com.projectit210.dto.request.TotpVerifyRequest;
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

import java.util.Map;

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
                        RedirectAttributes redirectAttributes,
                        jakarta.servlet.http.HttpServletRequest request) {
        if (bindingResult.hasErrors()) {
            return "auth/login";
        }
        try {
            User user = authService.login(loginRequest);

            // Kiểm tra nếu user đã bật 2FA → chuyển sang trang xác thực TOTP
            if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
                // Lưu userId tạm vào session để xác thực ở bước 2
                request.getSession().setAttribute("2FA_USER_ID", user.getId());
                request.getSession().setAttribute("2FA_USERNAME", user.getUsername());
                return "redirect:/auth/verify-2fa";
            }

            // Không có 2FA → tạo token và đăng nhập bình thường
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

    // ==================== 2FA VERIFICATION (LOGIN FLOW) ====================

    @GetMapping("/verify-2fa")
    public String verifyTwoFactorPage(jakarta.servlet.http.HttpServletRequest request, Model model) {
        // Kiểm tra session có chứa 2FA user ID không
        String userId = (String) request.getSession().getAttribute("2FA_USER_ID");
        if (userId == null) {
            return "redirect:/auth/login";
        }

        String username = (String) request.getSession().getAttribute("2FA_USERNAME");
        model.addAttribute("username", username);
        model.addAttribute("totpVerifyRequest", new TotpVerifyRequest());
        return "auth/verify-2fa";
    }

    @PostMapping("/verify-2fa")
    public String verifyTwoFactor(@Valid @ModelAttribute TotpVerifyRequest totpVerifyRequest,
                                  BindingResult bindingResult,
                                  jakarta.servlet.http.HttpServletRequest request,
                                  HttpServletResponse response,
                                  Model model) {
        String userId = (String) request.getSession().getAttribute("2FA_USER_ID");
        if (userId == null) {
            return "redirect:/auth/login";
        }

        if (bindingResult.hasErrors()) {
            model.addAttribute("username", request.getSession().getAttribute("2FA_USERNAME"));
            return "auth/verify-2fa";
        }

        try {
            User user = authService.verifyTotp(userId, totpVerifyRequest.getCode());

            // Xóa session attributes tạm
            request.getSession().removeAttribute("2FA_USER_ID");
            request.getSession().removeAttribute("2FA_USERNAME");

            // Tạo JWT token
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
            model.addAttribute("username", request.getSession().getAttribute("2FA_USERNAME"));
            return "auth/verify-2fa";
        }
    }

    // ==================== REGISTRATION ====================

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

    // ==================== LOGOUT ====================

    @GetMapping("/logout")
    public String logout(HttpServletResponse response, jakarta.servlet.http.HttpServletRequest request) {
        Cookie cookie = new Cookie(cookieName, null);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        // Invalidate session
        request.getSession().invalidate();

        return "redirect:/auth/login";
    }

    // ==================== 2FA SETUP (LOGGED-IN USERS) ====================

    @GetMapping("/2fa-setup")
    public String twoFactorSetupPage(jakarta.servlet.http.HttpServletRequest request, Model model) {
        User currentUser = (User) request.getAttribute(AppConstant.CURRENT_USER);
        if (currentUser == null) {
            return "redirect:/auth/login";
        }

        model.addAttribute("user", currentUser);
        model.addAttribute("twoFactorEnabled", currentUser.getTwoFactorEnabled());
        return "auth/2fa-setup";
    }

    @PostMapping("/2fa-setup/generate")
    public String generateTwoFactorSetup(jakarta.servlet.http.HttpServletRequest request,
                                         RedirectAttributes redirectAttributes) {
        User currentUser = (User) request.getAttribute(AppConstant.CURRENT_USER);
        if (currentUser == null) {
            return "redirect:/auth/login";
        }

        try {
            Map<String, String> setupData = authService.generateTwoFactorSetup(currentUser.getId());
            redirectAttributes.addFlashAttribute("secret", setupData.get("secret"));
            redirectAttributes.addFlashAttribute("qrImageBase64", setupData.get("qrImageBase64"));
        } catch (BadRequestException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/auth/2fa-setup";
    }

    @PostMapping("/2fa-setup/enable")
    public String enableTwoFactor(@ModelAttribute TotpVerifyRequest totpVerifyRequest,
                                  jakarta.servlet.http.HttpServletRequest request,
                                  RedirectAttributes redirectAttributes) {
        User currentUser = (User) request.getAttribute(AppConstant.CURRENT_USER);
        if (currentUser == null) {
            return "redirect:/auth/login";
        }

        try {
            authService.enableTwoFactor(currentUser.getId(), totpVerifyRequest.getCode());
            redirectAttributes.addFlashAttribute("successMessage", "Kích hoạt xác thực hai lớp thành công!");
        } catch (BadRequestException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            // Giữ lại thông tin QR để user thử lại
            try {
                Map<String, String> setupData = authService.generateTwoFactorSetup(currentUser.getId());
                redirectAttributes.addFlashAttribute("secret", setupData.get("secret"));
                redirectAttributes.addFlashAttribute("qrImageBase64", setupData.get("qrImageBase64"));
            } catch (Exception ignored) {
                // Nếu không thể generate lại thì bỏ qua
            }
        }

        return "redirect:/auth/2fa-setup";
    }

    @PostMapping("/2fa-setup/disable")
    public String disableTwoFactor(@ModelAttribute TotpVerifyRequest totpVerifyRequest,
                                   jakarta.servlet.http.HttpServletRequest request,
                                   RedirectAttributes redirectAttributes) {
        User currentUser = (User) request.getAttribute(AppConstant.CURRENT_USER);
        if (currentUser == null) {
            return "redirect:/auth/login";
        }

        try {
            authService.disableTwoFactor(currentUser.getId(), totpVerifyRequest.getCode());
            redirectAttributes.addFlashAttribute("successMessage", "Đã vô hiệu hóa xác thực hai lớp.");
        } catch (BadRequestException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/auth/2fa-setup";
    }
}
