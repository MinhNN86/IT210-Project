package com.projectit210.controller;

import com.projectit210.constant.AppConstant;
import com.projectit210.entity.Lecturer;
import com.projectit210.entity.MentoringSession;
import com.projectit210.entity.User;
import com.projectit210.enums.Role;
import com.projectit210.enums.SessionStatus;
import com.projectit210.exception.ResourceNotFoundException;
import com.projectit210.repository.LecturerRepository;
import com.projectit210.repository.MentoringSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/meeting")
@RequiredArgsConstructor
public class MeetingController {

    private final MentoringSessionRepository sessionRepository;
    private final LecturerRepository lecturerRepository;

    @GetMapping("/{sessionId}")
    @Transactional
    public String meetingRoom(@PathVariable Long sessionId,
                              HttpServletRequest request,
                              Model model,
                              RedirectAttributes redirectAttributes) {
        User user = (User) request.getAttribute(AppConstant.CURRENT_USER);

        MentoringSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Buổi tư vấn không tồn tại"));

        if (session.getStatus() != SessionStatus.CONFIRMED) {
            redirectAttributes.addFlashAttribute("errorMessage", "Buổi tư vấn chưa được xác nhận, không thể vào phòng họp.");
            return redirectByRole(user);
        }

        boolean isStudent = user.getRole() == Role.STUDENT;
        boolean isLecturer = user.getRole() == Role.LECTURER;

        if (isStudent) {
            if (!session.getStudent().getId().equals(user.getId())) {
                redirectAttributes.addFlashAttribute("errorMessage", "Bạn không có quyền tham gia buổi tư vấn này.");
                return "redirect:/student/sessions";
            }
            if (!session.getMeetingActive()) {
                redirectAttributes.addFlashAttribute("errorMessage", "Giảng viên chưa mở phòng họp. Vui lòng đợi giảng viên vào phòng.");
                return "redirect:/student/sessions";
            }
        } else if (isLecturer) {
            Lecturer lecturer = lecturerRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Giảng viên không tồn tại"));
            if (!session.getLecturer().getId().equals(lecturer.getId())) {
                redirectAttributes.addFlashAttribute("errorMessage", "Bạn không có quyền tham gia buổi tư vấn này.");
                return "redirect:/lecturer/confirmed-sessions";
            }
            session.setMeetingActive(true);
            sessionRepository.save(session);
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Bạn không có quyền truy cập.");
            return "redirect:/";
        }

        model.addAttribute("sessionId", session.getId());
        model.addAttribute("studentName", session.getStudent().getFullName());
        model.addAttribute("lecturerName", session.getLecturer().getUser().getFullName());
        model.addAttribute("sessionDate", session.getSessionDate().toString());
        model.addAttribute("startTime", session.getStartTime().toString());
        model.addAttribute("endTime", session.getEndTime().toString());
        model.addAttribute("userName", user.getFullName());
        model.addAttribute("userRole", user.getRole().name());

        return "meeting-room";
    }

    @PostMapping("/{sessionId}/close")
    @Transactional
    @ResponseBody
    public ResponseEntity<?> closeRoom(@PathVariable Long sessionId, HttpServletRequest request) {
        User user = (User) request.getAttribute(AppConstant.CURRENT_USER);

        MentoringSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Buổi tư vấn không tồn tại"));

        if (user.getRole() != Role.LECTURER) {
            return ResponseEntity.status(403).body(Map.of("error", "Chỉ giảng viên mới có thể đóng phòng"));
        }

        Lecturer lecturer = lecturerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Giảng viên không tồn tại"));
        if (!session.getLecturer().getId().equals(lecturer.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Bạn không có quyền đóng phòng này"));
        }

        session.setMeetingActive(false);
        sessionRepository.save(session);

        return ResponseEntity.ok(Map.of("success", true, "message", "Đã đóng phòng họp"));
    }

    @GetMapping("/{sessionId}/status")
    @ResponseBody
    public ResponseEntity<?> meetingStatus(@PathVariable Long sessionId, HttpServletRequest request) {
        User user = (User) request.getAttribute(AppConstant.CURRENT_USER);

        MentoringSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Buổi tư vấn không tồn tại"));

        if (!session.getStudent().getId().equals(user.getId())
                && !lecturerRepository.findByUserId(user.getId())
                        .map(l -> l.getId().equals(session.getLecturer().getId())).orElse(false)
                && user.getRole() != Role.ADMIN) {
            return ResponseEntity.status(403).body(Map.of("error", "Không có quyền"));
        }

        return ResponseEntity.ok(Map.of(
                "meetingActive", session.getMeetingActive(),
                "sessionId", session.getId()
        ));
    }

    private String redirectByRole(User user) {
        return switch (user.getRole()) {
            case LECTURER -> "redirect:/lecturer/confirmed-sessions";
            case STUDENT -> "redirect:/student/sessions";
            case ADMIN -> "redirect:/admin/dashboard";
        };
    }
}
