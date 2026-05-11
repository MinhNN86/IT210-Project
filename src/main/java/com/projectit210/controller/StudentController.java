package com.projectit210.controller;

import com.projectit210.constant.AppConstant;
import com.projectit210.dto.request.CreateSessionRequest;
import com.projectit210.entity.Lecturer;
import com.projectit210.entity.User;
import com.projectit210.exception.BadRequestException;
import com.projectit210.exception.ConflictException;
import com.projectit210.repository.DepartmentRepository;
import com.projectit210.repository.LecturerRepository;
import com.projectit210.service.AcademicEvaluationService;
import com.projectit210.service.MentoringSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Controller
@RequestMapping("/student")
@RequiredArgsConstructor
public class StudentController {

    private final MentoringSessionService sessionService;
    private final AcademicEvaluationService evaluationService;
    private final DepartmentRepository departmentRepository;
    private final LecturerRepository lecturerRepository;

    @GetMapping("/dashboard")
    public String dashboard(HttpServletRequest request, Model model) {
        User user = (User) request.getAttribute(AppConstant.CURRENT_USER);
        model.addAttribute("user", user);
        List<com.projectit210.dto.response.SessionResponse> sessions = sessionService.getSessionsByStudent(user.getId());
        model.addAttribute("sessions", sessions);
        model.addAttribute("pendingCount", sessions.stream().filter(s -> "PENDING".equals(s.getStatus())).count());
        model.addAttribute("completedCount", sessions.stream().filter(s -> "COMPLETED".equals(s.getStatus())).count());
        return "student/dashboard";
    }

    @GetMapping("/book")
    public String bookingForm(Model model) {
        model.addAttribute("createSessionRequest", new CreateSessionRequest());
        model.addAttribute("departments", departmentRepository.findAll());

        List<java.util.Map<String, Object>> lecturerList = lecturerRepository.findAllWithDetails().stream()
                .map(l -> {
                    java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("id", l.getId());
                    map.put("fullName", l.getUser().getFullName());
                    map.put("academicRank", l.getAcademicRank() != null ? l.getAcademicRank() : "");
                    map.put("departmentId", l.getDepartment().getId());
                    return map;
                })
                .toList();
        model.addAttribute("lecturerList", lecturerList);
        return "student/booking-form";
    }

    /** API AJAX: Lấy danh sách giảng viên theo khoa */
    @GetMapping("/api/lecturers")
    @ResponseBody
    public List<java.util.Map<String, Object>> getLecturersByDepartment(@RequestParam Long departmentId) {
        return lecturerRepository.findByDepartmentIdWithDetails(departmentId).stream()
                .map(l -> {
                    java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("id", l.getId());
                    map.put("fullName", l.getUser().getFullName());
                    map.put("academicRank", l.getAcademicRank());
                    return map;
                })
                .toList();
    }

    /** API AJAX: Lấy danh sách slot đã đặt trong ngày */
    @GetMapping("/api/booked-slots")
    @ResponseBody
    public List<LocalTime> getBookedSlots(@RequestParam Long lecturerId,
                                          @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate date) {
        return sessionService.getBookedSlots(lecturerId, date);
    }

    @PostMapping("/book")
    public String createBooking(@Valid @ModelAttribute CreateSessionRequest createSessionRequest,
                                BindingResult bindingResult,
                                HttpServletRequest request,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("departments", departmentRepository.findAll());
            return "student/booking-form";
        }
        try {
            User user = (User) request.getAttribute(AppConstant.CURRENT_USER);
            sessionService.createSession(user.getId(), createSessionRequest);
            redirectAttributes.addFlashAttribute("successMessage", "Đặt lịch thành công!");
            return "redirect:/student/sessions";
        } catch (BadRequestException | ConflictException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("departments", departmentRepository.findAll());
            return "student/booking-form";
        }
    }

    @GetMapping("/sessions")
    public String mySessions(HttpServletRequest request, Model model) {
        User user = (User) request.getAttribute(AppConstant.CURRENT_USER);
        model.addAttribute("sessions", sessionService.getSessionsByStudent(user.getId()));
        return "student/my-sessions";
    }

    @PostMapping("/sessions/{id}/cancel")
    public String cancelSession(@PathVariable Long id,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        try {
            User user = (User) request.getAttribute(AppConstant.CURRENT_USER);
            sessionService.cancelSession(id, user.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Hủy lịch thành công! Slot đã được giải phóng.");
        } catch (BadRequestException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/student/sessions";
    }

    @GetMapping("/academic-history")
    public String academicHistory(HttpServletRequest request, Model model) {
        User user = (User) request.getAttribute(AppConstant.CURRENT_USER);
        model.addAttribute("user", user);
        model.addAttribute("history", evaluationService.getAcademicHistory(user.getId()));
        return "student/academic-history";
    }
}
