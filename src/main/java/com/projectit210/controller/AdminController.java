package com.projectit210.controller;

import com.projectit210.constant.AppConstant;
import com.projectit210.dto.request.EquipmentRequest;
import com.projectit210.entity.Department;
import com.projectit210.entity.Lecturer;
import com.projectit210.entity.User;
import com.projectit210.enums.Role;
import com.projectit210.exception.BadRequestException;
import com.projectit210.repository.DepartmentRepository;
import com.projectit210.repository.LecturerRepository;
import com.projectit210.service.BorrowingService;
import com.projectit210.service.DashboardService;
import com.projectit210.service.DepartmentService;
import com.projectit210.service.EquipmentService;
import com.projectit210.service.UserService;
import com.projectit210.dto.request.DepartmentRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final EquipmentService equipmentService;
    private final BorrowingService borrowingService;
    private final UserService userService;
    private final LecturerRepository lecturerRepository;
    private final DepartmentRepository departmentRepository;
    private final DepartmentService departmentService;
    private final PasswordEncoder passwordEncoder;
    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    public String dashboard(HttpServletRequest request, Model model) {
        User user = (User) request.getAttribute(AppConstant.CURRENT_USER);
        model.addAttribute("user", user);
        model.addAttribute("pendingBorrowings", borrowingService.getPendingDispatch());

        // ===================== DASHBOARD STATISTICS =====================
        // Tất cả thống kê đều lấy trực tiếp từ Database bằng SQL nâng cao
        // (JOIN, GROUP BY, HAVING, SUM, COUNT, ORDER BY)
        // Không sử dụng vòng lặp for trong Java để tính toán tổng

        // Thống kê cơ bản (sử dụng COUNT query trực tiếp từ DB)
        model.addAttribute("totalEquipments", equipmentService.findAll().size());
        model.addAttribute("totalStudents", dashboardService.countStudents());
        model.addAttribute("totalLecturers", dashboardService.countLecturers());

        // Thống kê thiết bị đang mượn (sử dụng COUNT + SUM từ DB)
        model.addAttribute("borrowedRecordCount", dashboardService.countBorrowedEquipments());
        model.addAttribute("borrowedEquipmentQuantity", dashboardService.sumBorrowedEquipmentQuantity());

        // Thống kê buổi tư vấn (sử dụng COUNT từ DB)
        model.addAttribute("totalActiveSessions", dashboardService.countActiveSessions());

        // Top 5 giảng viên (JOIN + GROUP BY + HAVING + ORDER BY từ DB)
        model.addAttribute("top5Lecturers", dashboardService.getTop5Lecturers());

        // Thống kê phiếu mượn theo trạng thái (GROUP BY từ DB)
        model.addAttribute("borrowingStatsByStatus", dashboardService.getBorrowingStatsByStatus());

        // Thống kê session theo trạng thái (GROUP BY từ DB)
        model.addAttribute("sessionStatsByStatus", dashboardService.getSessionStatsByStatus());

        // Thống kê thiết bị đang mượn theo loại (JOIN + GROUP BY + SUM từ DB)
        model.addAttribute("borrowedByEquipmentStats", dashboardService.getBorrowedByEquipmentStats());

        return "admin/dashboard";
    }

    // ===================== USER MANAGEMENT (CRUD Students & Lecturers) =====================

    @GetMapping("/users")
    public String userList(@RequestParam(required = false) String role, Model model) {
        List<User> users;
        if (role != null) {
            try {
                Role filterRole = Role.valueOf(role.toUpperCase());
                users = userService.findByRole(filterRole);
            } catch (IllegalArgumentException e) {
                users = userService.findAll();
            }
        } else {
            users = userService.findAll();
        }
        // Exclude ADMIN users from the list
        users = users.stream().filter(u -> u.getRole() != Role.ADMIN).toList();
        model.addAttribute("users", users);
        model.addAttribute("lecturerDetails", lecturerRepository.findAllWithDetails());
        model.addAttribute("departments", departmentRepository.findAll());
        model.addAttribute("selectedRole", role);
        return "admin/users";
    }

    @GetMapping("/users/new")
    public String newUserForm(@RequestParam(required = false) String role, Model model) {
        User user = new User();
        model.addAttribute("user", user);
        model.addAttribute("departments", departmentRepository.findAll());
        model.addAttribute("isEdit", false);
        model.addAttribute("selectedRole", role != null ? role : "STUDENT");
        return "admin/user-form";
    }

    @PostMapping("/users")
    public String createUser(@ModelAttribute User user,
                             @RequestParam(required = false) Long departmentId,
                             @RequestParam(required = false) String academicRank,
                             @RequestParam(required = false) String specialization,
                             @RequestParam(defaultValue = "STUDENT") String role,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        try {
            // Validate
            if (user.getUsername() == null || user.getUsername().isBlank()) {
                throw new BadRequestException("Tên đăng nhập không được để trống");
            }
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                throw new BadRequestException("Email không được để trống");
            }
            if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
                throw new BadRequestException("Mật khẩu không được để trống");
            }
            if (user.getPasswordHash().length() < 6) {
                throw new BadRequestException("Mật khẩu phải từ 6 ký tự trở lên");
            }
            if (user.getFullName() == null || user.getFullName().isBlank()) {
                throw new BadRequestException("Họ tên không được để trống");
            }

            // Check duplicate
            if (userService.findByUsername(user.getUsername()).isPresent()) {
                throw new BadRequestException("Tên đăng nhập đã tồn tại");
            }
            if (userService.findAll().stream().anyMatch(u -> u.getEmail().equals(user.getEmail()))) {
                throw new BadRequestException("Email đã được sử dụng");
            }

            // Set role
            Role userRole;
            try {
                userRole = Role.valueOf(role.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Vai trò không hợp lệ");
            }
            if (userRole == Role.ADMIN) {
                throw new BadRequestException("Không thể tạo tài khoản Admin qua form này");
            }

            user.setRole(userRole);
            user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
            user.setIsActive(true);

            User savedUser = userService.save(user);

            // If lecturer, create Lecturer record
            if (userRole == Role.LECTURER) {
                if (departmentId == null) {
                    throw new BadRequestException("Giảng viên phải chọn Khoa/Ngành");
                }
                Department dept = departmentRepository.findById(departmentId)
                        .orElseThrow(() -> new BadRequestException("Khoa/Ngành không tồn tại"));
                Lecturer lecturer = Lecturer.builder()
                        .user(savedUser)
                        .department(dept)
                        .academicRank(academicRank)
                        .specialization(specialization)
                        .build();
                lecturerRepository.save(lecturer);
            }

            redirectAttributes.addFlashAttribute("successMessage", "Tạo tài khoản thành công!");
            return "redirect:/admin/users";
        } catch (BadRequestException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("departments", departmentRepository.findAll());
            model.addAttribute("isEdit", false);
            model.addAttribute("selectedRole", role);
            return "admin/user-form";
        }
    }

    @GetMapping("/users/{id}/edit")
    public String editUserForm(@PathVariable String id, Model model) {
        User user = userService.findById(id)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng"));
        model.addAttribute("user", user);
        model.addAttribute("departments", departmentRepository.findAll());
        model.addAttribute("isEdit", true);

        // Load lecturer details if applicable
        if (user.getRole() == Role.LECTURER) {
            lecturerRepository.findByUserId(id).ifPresent(l -> {
                model.addAttribute("lecturerDetail", l);
                model.addAttribute("selectedRole", "LECTURER");
            });
        } else {
            model.addAttribute("selectedRole", "STUDENT");
        }

        return "admin/user-form";
    }

    @PostMapping("/users/{id}")
    public String updateUser(@PathVariable String id,
                             @ModelAttribute User user,
                             @RequestParam(required = false) Long departmentId,
                             @RequestParam(required = false) String academicRank,
                             @RequestParam(required = false) String specialization,
                             @RequestParam(required = false) String newPassword,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        User existing = null;
        try {
            existing = userService.findById(id)
                    .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng"));

            // Validate
            if (user.getFullName() == null || user.getFullName().isBlank()) {
                throw new BadRequestException("Họ tên không được để trống");
            }
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                throw new BadRequestException("Email không được để trống");
            }

            // Check email duplicate (exclude self)
            if (userService.findAll().stream()
                    .anyMatch(u -> u.getEmail().equals(user.getEmail()) && !u.getId().equals(id))) {
                throw new BadRequestException("Email đã được sử dụng");
            }

            existing.setFullName(user.getFullName());
            existing.setEmail(user.getEmail());
            existing.setPhone(user.getPhone());
            existing.setIsActive(user.getIsActive());

            // Update password only if provided
            if (newPassword != null && !newPassword.isBlank()) {
                if (newPassword.length() < 6) {
                    throw new BadRequestException("Mật khẩu phải từ 6 ký tự trở lên");
                }
                existing.setPasswordHash(passwordEncoder.encode(newPassword));
            }

            userService.save(existing);

            // Update lecturer details if applicable
            if (existing.getRole() == Role.LECTURER) {
                Lecturer lecturer = lecturerRepository.findByUserId(id).orElse(null);
                if (lecturer != null && departmentId != null) {
                    Department dept = departmentRepository.findById(departmentId)
                            .orElseThrow(() -> new BadRequestException("Khoa/Ngành không tồn tại"));
                    lecturer.setDepartment(dept);
                    lecturer.setAcademicRank(academicRank);
                    lecturer.setSpecialization(specialization);
                    lecturerRepository.save(lecturer);
                }
            }

            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật tài khoản thành công!");
            return "redirect:/admin/users";
        } catch (BadRequestException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("departments", departmentRepository.findAll());
            model.addAttribute("isEdit", true);
            model.addAttribute("selectedRole", existing != null ? existing.getRole().name() : "STUDENT");
            return "admin/user-form";
        }
    }

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            User user = userService.findById(id)
                    .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng"));
            if (user.getRole() == Role.ADMIN) {
                throw new BadRequestException("Không thể xóa tài khoản Admin");
            }
            // Delete lecturer record first if exists
            if (user.getRole() == Role.LECTURER) {
                lecturerRepository.findByUserId(id).ifPresent(lecturerRepository::delete);
            }
            userService.deleteById(id);
            redirectAttributes.addFlashAttribute("successMessage", "Xóa tài khoản thành công!");
        } catch (BadRequestException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/toggle-status")
    public String toggleUserStatus(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            User user = userService.findById(id)
                    .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng"));
            if (user.getRole() == Role.ADMIN) {
                throw new BadRequestException("Không thể thay đổi trạng thái tài khoản Admin");
            }
            user.setIsActive(!user.getIsActive());
            userService.save(user);
            String status = user.getIsActive() ? "kích hoạt" : "vô hiệu hóa";
            redirectAttributes.addFlashAttribute("successMessage", "Đã " + status + " tài khoản thành công!");
        } catch (BadRequestException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    // ===================== EQUIPMENT CRUD (CORE-04) =====================

    @GetMapping("/equipments")
    public String equipmentList(Model model) {
        model.addAttribute("equipments", equipmentService.findAll());
        return "admin/equipments";
    }

    @GetMapping("/equipments/new")
    public String newEquipmentForm(Model model) {
        model.addAttribute("equipmentRequest", new EquipmentRequest());
        model.addAttribute("isEdit", false);
        return "admin/equipment-form";
    }

    @PostMapping("/equipments")
    public String createEquipment(@Valid @ModelAttribute EquipmentRequest equipmentRequest,
                                  BindingResult bindingResult,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", false);
            return "admin/equipment-form";
        }
        try {
            equipmentService.create(equipmentRequest);
            redirectAttributes.addFlashAttribute("successMessage", "Thêm thiết bị thành công!");
        } catch (BadRequestException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("isEdit", false);
            return "admin/equipment-form";
        }
        return "redirect:/admin/equipments";
    }

    @GetMapping("/equipments/{id}/edit")
    public String editEquipmentForm(@PathVariable Long id, Model model) {
        var eq = equipmentService.findById(id);
        EquipmentRequest request = EquipmentRequest.builder()
                .code(eq.getCode()).name(eq.getName())
                .description(eq.getDescription())
                .quantityInStock(eq.getQuantityInStock())
                .minimumStock(eq.getMinimumStock()).build();
        model.addAttribute("equipmentRequest", request);
        model.addAttribute("equipmentId", id);
        model.addAttribute("isEdit", true);
        return "admin/equipment-form";
    }

    @PostMapping("/equipments/{id}")
    public String updateEquipment(@PathVariable Long id,
                                  @Valid @ModelAttribute EquipmentRequest equipmentRequest,
                                  BindingResult bindingResult,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("equipmentId", id);
            model.addAttribute("isEdit", true);
            return "admin/equipment-form";
        }
        try {
            equipmentService.update(id, equipmentRequest);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật thiết bị thành công!");
        } catch (BadRequestException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("equipmentId", id);
            model.addAttribute("isEdit", true);
            return "admin/equipment-form";
        }
        return "redirect:/admin/equipments";
    }

    @PostMapping("/equipments/{id}/delete")
    public String deleteEquipment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        equipmentService.delete(id);
        redirectAttributes.addFlashAttribute("successMessage", "Xóa thiết bị thành công!");
        return "redirect:/admin/equipments";
    }

    // ===================== BORROWING MANAGEMENT (CORE-08) =====================

    @GetMapping("/borrowings")
    public String borrowingManagement(Model model) {
        model.addAttribute("borrowings", borrowingService.getAllBorrowings());
        model.addAttribute("pendingBorrowings", borrowingService.getPendingDispatch());
        model.addAttribute("dispatchedBorrowings", borrowingService.getDispatched());
        return "admin/borrowing-management";
    }

    @PostMapping("/borrowings/{id}/approve")
    public String approveDispatch(@PathVariable Long id,
                                  HttpServletRequest request,
                                  RedirectAttributes redirectAttributes) {
        try {
            User user = (User) request.getAttribute(AppConstant.CURRENT_USER);
            borrowingService.approveDispatch(id, user.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Xác nhận xuất kho thành công!");
        } catch (BadRequestException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/borrowings";
    }

    @PostMapping("/borrowings/{id}/reject")
    public String rejectBorrowing(@PathVariable Long id,
                                  HttpServletRequest request,
                                  RedirectAttributes redirectAttributes) {
        try {
            User user = (User) request.getAttribute(AppConstant.CURRENT_USER);
            borrowingService.rejectBorrowing(id, user.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Đã từ chối phiếu mượn!");
        } catch (BadRequestException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/borrowings";
    }

    @PostMapping("/borrowings/{id}/return")
    public String returnEquipment(@PathVariable Long id,
                                  HttpServletRequest request,
                                  RedirectAttributes redirectAttributes) {
        try {
            User user = (User) request.getAttribute(AppConstant.CURRENT_USER);
            borrowingService.returnEquipment(id, user.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Xác nhận trả thiết bị thành công!");
        } catch (BadRequestException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/borrowings";
    }
    // ===================== DEPARTMENT CRUD =====================

    @GetMapping("/departments")
    public String departmentList(Model model) {
        model.addAttribute("departments", departmentService.findAll());
        return "admin/departments";
    }

    @GetMapping("/departments/new")
    public String newDepartmentForm(Model model) {
        model.addAttribute("departmentRequest", new DepartmentRequest());
        model.addAttribute("isEdit", false);
        return "admin/department-form";
    }

    @PostMapping("/departments")
    public String createDepartment(@Valid @ModelAttribute DepartmentRequest departmentRequest,
                                  BindingResult bindingResult,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("isEdit", false);
            return "admin/department-form";
        }
        try {
            departmentService.create(departmentRequest);
            redirectAttributes.addFlashAttribute("successMessage", "Thêm Khoa/Ngành thành công!");
        } catch (BadRequestException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("isEdit", false);
            return "admin/department-form";
        }
        return "redirect:/admin/departments";
    }

    @GetMapping("/departments/{id}/edit")
    public String editDepartmentForm(@PathVariable Long id, Model model) {
        Department dept = departmentService.findById(id);
        DepartmentRequest request = DepartmentRequest.builder()
                .code(dept.getCode()).name(dept.getName()).build();
        model.addAttribute("departmentRequest", request);
        model.addAttribute("departmentId", id);
        model.addAttribute("isEdit", true);
        return "admin/department-form";
    }

    @PostMapping("/departments/{id}")
    public String updateDepartment(@PathVariable Long id,
                                  @Valid @ModelAttribute DepartmentRequest departmentRequest,
                                  BindingResult bindingResult,
                                  Model model,
                                  RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("departmentId", id);
            model.addAttribute("isEdit", true);
            return "admin/department-form";
        }
        try {
            departmentService.update(id, departmentRequest);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật Khoa/Ngành thành công!");
        } catch (BadRequestException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("departmentId", id);
            model.addAttribute("isEdit", true);
            return "admin/department-form";
        }
        return "redirect:/admin/departments";
    }

    @PostMapping("/departments/{id}/delete")
    public String deleteDepartment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            departmentService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Xóa Khoa/Ngành thành công!");
        } catch (BadRequestException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/departments";
    }
}
