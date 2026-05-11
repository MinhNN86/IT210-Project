package com.projectit210.controller;

import com.projectit210.constant.AppConstant;
import com.projectit210.dto.request.EvaluationRequest;
import com.projectit210.entity.Lecturer;
import com.projectit210.entity.MentoringSession;
import com.projectit210.entity.User;
import com.projectit210.enums.SessionStatus;
import com.projectit210.exception.BadRequestException;
import com.projectit210.exception.ResourceNotFoundException;
import com.projectit210.repository.LecturerRepository;
import com.projectit210.repository.MentoringSessionRepository;
import com.projectit210.service.AcademicEvaluationService;
import com.projectit210.service.EquipmentService;
import com.projectit210.service.MentoringSessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/lecturer")
@RequiredArgsConstructor
public class LecturerController {

    private final MentoringSessionService sessionService;
    private final AcademicEvaluationService evaluationService;
    private final EquipmentService equipmentService;
    private final LecturerRepository lecturerRepository;
    private final MentoringSessionRepository sessionRepository;

    @GetMapping("/dashboard")
    public String dashboard(HttpServletRequest request, Model model) {
        User user = (User) request.getAttribute(AppConstant.CURRENT_USER);
        Lecturer lecturer = lecturerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Giảng viên không tồn tại"));
        model.addAttribute("user", user);
        model.addAttribute("lecturer", lecturer);
        model.addAttribute("pendingSessions", sessionService.getPendingSessionsByLecturer(lecturer.getId()));
        model.addAttribute("confirmedSessions", sessionService.getConfirmedSessionsByLecturer(lecturer.getId()));
        return "lecturer/dashboard";
    }

    @GetMapping("/pending-sessions")
    public String pendingSessions(HttpServletRequest request, Model model) {
        User user = (User) request.getAttribute(AppConstant.CURRENT_USER);
        Lecturer lecturer = lecturerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Giảng viên không tồn tại"));
        model.addAttribute("sessions", sessionService.getPendingSessionsByLecturer(lecturer.getId()));
        return "lecturer/pending-sessions";
    }

    @GetMapping("/confirmed-sessions")
    public String confirmedSessions(HttpServletRequest request, Model model) {
        User user = (User) request.getAttribute(AppConstant.CURRENT_USER);
        Lecturer lecturer = lecturerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Giảng viên không tồn tại"));
        model.addAttribute("sessions", sessionService.getConfirmedSessionsByLecturer(lecturer.getId()));
        return "lecturer/confirmed-sessions";
    }

    @GetMapping("/evaluate/{sessionId}")
    public String evaluationForm(@PathVariable Long sessionId, Model model, RedirectAttributes redirectAttributes) {
        MentoringSession mentoringSession = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Buổi tư vấn không tồn tại"));
        if (mentoringSession.getStatus() != SessionStatus.CONFIRMED) {
            redirectAttributes.addFlashAttribute("errorMessage", "Buổi tư vấn phải được xác nhận trước khi đánh giá");
            return "redirect:/lecturer/pending-sessions";
        }
        model.addAttribute("mentoringSession", mentoringSession);
        model.addAttribute("evaluationRequest", new EvaluationRequest());
        model.addAttribute("equipments", equipmentService.findAllActive());
        return "lecturer/evaluation-form";
    }

    @PostMapping("/cancel-session/{sessionId}")
    public String cancelSession(@PathVariable Long sessionId,
                                @RequestParam String reason,
                                HttpServletRequest request,
                                RedirectAttributes redirectAttributes) {
        try {
            User user = (User) request.getAttribute(AppConstant.CURRENT_USER);
            Lecturer lecturer = lecturerRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Giảng viên không tồn tại"));
            sessionService.cancelSessionByLecturer(sessionId, lecturer.getId(), reason);
            redirectAttributes.addFlashAttribute("successMessage", "Đã hủy buổi tư vấn thành công!");
        } catch (BadRequestException | ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/lecturer/pending-sessions";
    }

    @PostMapping("/confirm-session/{sessionId}")
    public String confirmSession(@PathVariable Long sessionId,
                                  HttpServletRequest request,
                                  RedirectAttributes redirectAttributes) {
        try {
            User user = (User) request.getAttribute(AppConstant.CURRENT_USER);
            Lecturer lecturer = lecturerRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Giảng viên không tồn tại"));
            sessionService.confirmSession(sessionId, lecturer.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Đã xác nhận buổi tư vấn thành công!");
        } catch (BadRequestException | ResourceNotFoundException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/lecturer/pending-sessions";
    }

    @PostMapping("/evaluate")
    public String submitEvaluation(@ModelAttribute EvaluationRequest evaluationRequest,
                                   HttpServletRequest request,
                                   RedirectAttributes redirectAttributes) {
        try {
            User user = (User) request.getAttribute(AppConstant.CURRENT_USER);
            Lecturer lecturer = lecturerRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Giảng viên không tồn tại"));
            evaluationService.completeEvaluation(lecturer.getId(), evaluationRequest);
            redirectAttributes.addFlashAttribute("successMessage", "Đánh giá và tạo phiếu mượn thành công!");
        } catch (BadRequestException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/lecturer/pending-sessions";
    }
}
