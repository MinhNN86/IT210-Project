package com.projectit210.config;

import com.projectit210.entity.Department;
import com.projectit210.entity.Equipment;
import com.projectit210.entity.Lecturer;
import com.projectit210.entity.User;
import com.projectit210.enums.Role;
import com.projectit210.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final LecturerRepository lecturerRepository;
    private final EquipmentRepository equipmentRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (departmentRepository.count() == 0) {
            log.info("=== Khởi tạo dữ liệu seed ===");
            seedDepartments();
            seedAdmin();
            seedEquipments();
            seedLecturers();
            log.info("=== Hoàn tất khởi tạo dữ liệu ===");
        }
    }

    private void seedDepartments() {
        departmentRepository.save(Department.builder().code("CNTT").name("Khoa Công nghệ Thông tin").build());
        departmentRepository.save(Department.builder().code("DTVT").name("Khoa Điện tử Viễn thông").build());
        departmentRepository.save(Department.builder().code("CK").name("Khoa Cơ khí").build());
        departmentRepository.save(Department.builder().code("KT").name("Khoa Kinh tế").build());
        departmentRepository.save(Department.builder().code("NN").name("Khoa Ngoại ngữ").build());
        log.info("Đã tạo 5 Khoa/Ngành");
    }

    private void seedAdmin() {
        User admin = User.builder()
                .username("admin")
                .email("admin@university.edu.vn")
                .passwordHash(passwordEncoder.encode("admin123"))
                .role(Role.ADMIN)
                .fullName("Quản trị viên")
                .isActive(true)
                .build();
        userRepository.save(admin);
        log.info("Đã tạo tài khoản Admin (admin/admin123)");
    }

    private void seedEquipments() {
        equipmentRepository.save(Equipment.builder().code("LAP-001").name("Laptop Dell Latitude").description("Laptop Dell 15 inch").quantityInStock(20).minimumStock(5).build());
        equipmentRepository.save(Equipment.builder().code("OSC-001").name("Máy hiện sóng Tektronix").description("Oscilloscope 100MHz").quantityInStock(10).minimumStock(2).build());
        equipmentRepository.save(Equipment.builder().code("ARD-001").name("Arduino Uno R3").description("Board vi điều khiển Arduino").quantityInStock(50).minimumStock(10).build());
        equipmentRepository.save(Equipment.builder().code("RPI-001").name("Raspberry Pi 4").description("Máy tính nhúng Raspberry Pi").quantityInStock(15).minimumStock(3).build());
        equipmentRepository.save(Equipment.builder().code("MUL-001").name("Đồng hồ vạn năng").description("Multimeter đo điện").quantityInStock(30).minimumStock(5).build());
        equipmentRepository.save(Equipment.builder().code("SDK-001").name("Bộ Kit IoT").description("Bộ kit thực hành IoT").quantityInStock(12).minimumStock(2).build());
        log.info("Đã tạo 6 thiết bị mẫu");
    }

    private void seedLecturers() {
        Department cntt = departmentRepository.findByCode("CNTT").orElse(null);
        Department dtvt = departmentRepository.findByCode("DTVT").orElse(null);
        if (cntt == null || dtvt == null) return;

        User lec1 = userRepository.save(User.builder().username("nguyenvana").email("nguyenvana@uni.edu.vn").passwordHash(passwordEncoder.encode("123456")).role(Role.LECTURER).fullName("TS. Nguyễn Văn A").isActive(true).build());
        lecturerRepository.save(Lecturer.builder().user(lec1).department(cntt).academicRank("Tiến sĩ").specialization("Trí tuệ nhân tạo, Machine Learning").build());

        User lec2 = userRepository.save(User.builder().username("tranthib").email("tranthib@uni.edu.vn").passwordHash(passwordEncoder.encode("123456")).role(Role.LECTURER).fullName("PGS.TS. Trần Thị B").isActive(true).build());
        lecturerRepository.save(Lecturer.builder().user(lec2).department(cntt).academicRank("Phó Giáo sư").specialization("An toàn thông tin, Mạng máy tính").build());

        User lec3 = userRepository.save(User.builder().username("levanc").email("levanc@uni.edu.vn").passwordHash(passwordEncoder.encode("123456")).role(Role.LECTURER).fullName("ThS. Lê Văn C").isActive(true).build());
        lecturerRepository.save(Lecturer.builder().user(lec3).department(dtvt).academicRank("Thạc sĩ").specialization("Điện tử công suất, Vi mạch").build());

        log.info("Đã tạo 3 giảng viên mẫu");
    }
}
