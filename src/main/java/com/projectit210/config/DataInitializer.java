package com.projectit210.config;

import com.projectit210.entity.Department;
import com.projectit210.entity.Equipment;
import com.projectit210.entity.Lecturer;
import com.projectit210.entity.User;
import com.projectit210.enums.Gender;
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
            seedStudents();
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

    private void seedStudents() {
        // Sinh viên Khoa Công nghệ Thông tin
        userRepository.save(User.builder()
                .username("phamminhk").email("phamminhk@uni.edu.vn").passwordHash(passwordEncoder.encode("123456"))
                .role(Role.STUDENT).fullName("Phạm Minh K").gender(Gender.MALE).phone("0901234567")
                .dob(java.time.LocalDate.of(2003, 3, 15)).address("123 Nguyễn Văn Cừ, Quận 5, TP.HCM").isActive(true).build());

        userRepository.save(User.builder()
                .username("nguyenthilinh").email("nguyenthilinh@uni.edu.vn").passwordHash(passwordEncoder.encode("123456"))
                .role(Role.STUDENT).fullName("Nguyễn Thị Linh").gender(Gender.FEMALE).phone("0912345678")
                .dob(java.time.LocalDate.of(2003, 7, 22)).address("456 Lý Thường Kiệt, Quận 10, TP.HCM").isActive(true).build());

        userRepository.save(User.builder()
                .username("tranvanhau").email("tranvanhau@uni.edu.vn").passwordHash(passwordEncoder.encode("123456"))
                .role(Role.STUDENT).fullName("Trần Văn Hậu").gender(Gender.MALE).phone("0923456789")
                .dob(java.time.LocalDate.of(2002, 11, 8)).address("789 Trần Hưng Đạo, Quận 1, TP.HCM").isActive(true).build());

        userRepository.save(User.builder()
                .username("levanthanh").email("levanthanh@uni.edu.vn").passwordHash(passwordEncoder.encode("123456"))
                .role(Role.STUDENT).fullName("Lê Văn Thanh").gender(Gender.MALE).phone("0934567890")
                .dob(java.time.LocalDate.of(2003, 1, 30)).address("321 Hoàng Văn Thụ, Quận Phú Nhuận, TP.HCM").isActive(true).build());

        userRepository.save(User.builder()
                .username("phamthimai").email("phamthimai@uni.edu.vn").passwordHash(passwordEncoder.encode("123456"))
                .role(Role.STUDENT).fullName("Phạm Thị Mai").gender(Gender.FEMALE).phone("0945678901")
                .dob(java.time.LocalDate.of(2003, 5, 12)).address("654 Điện Biên Phủ, Quận Bình Thạnh, TP.HCM").isActive(true).build());

        // Sinh viên Khoa Điện tử Viễn thông
        userRepository.save(User.builder()
                .username("huynhductai").email("huynhductai@uni.edu.vn").passwordHash(passwordEncoder.encode("123456"))
                .role(Role.STUDENT).fullName("Huỳnh Đức Tài").gender(Gender.MALE).phone("0956789012")
                .dob(java.time.LocalDate.of(2002, 9, 25)).address("987 Cách Mạng Tháng 8, Quận 3, TP.HCM").isActive(true).build());

        userRepository.save(User.builder()
                .username("vothithao").email("vothithao@uni.edu.vn").passwordHash(passwordEncoder.encode("123456"))
                .role(Role.STUDENT).fullName("Võ Thị Thảo").gender(Gender.FEMALE).phone("0967890123")
                .dob(java.time.LocalDate.of(2003, 12, 3)).address("159 Nguyễn Trãi, Quận 5, TP.HCM").isActive(true).build());

        // Sinh viên Khoa Cơ khí
        userRepository.save(User.builder()
                .username("dangquangvinh").email("dangquangvinh@uni.edu.vn").passwordHash(passwordEncoder.encode("123456"))
                .role(Role.STUDENT).fullName("Đặng Quang Vinh").gender(Gender.MALE).phone("0978901234")
                .dob(java.time.LocalDate.of(2002, 6, 18)).address("75 Võ Văn Tần, Quận 3, TP.HCM").isActive(true).build());

        userRepository.save(User.builder()
                .username("ngohuynhson").email("ngohuynhson@uni.edu.vn").passwordHash(passwordEncoder.encode("123456"))
                .role(Role.STUDENT).fullName("Ngô Huỳnh Sơn").gender(Gender.MALE).phone("0989012345")
                .dob(java.time.LocalDate.of(2003, 4, 7)).address("200 Nguyễn Thị Minh Khai, Quận 1, TP.HCM").isActive(true).build());

        // Sinh viên Khoa Kinh tế
        userRepository.save(User.builder()
                .username("truongthihong").email("truongthihong@uni.edu.vn").passwordHash(passwordEncoder.encode("123456"))
                .role(Role.STUDENT).fullName("Trương Thị Hồng").gender(Gender.FEMALE).phone("0990123456")
                .dob(java.time.LocalDate.of(2003, 8, 14)).address("55 Lê Lợi, Quận 1, TP.HCM").isActive(true).build());

        userRepository.save(User.builder()
                .username("lyquochuy").email("lyquochuy@uni.edu.vn").passwordHash(passwordEncoder.encode("123456"))
                .role(Role.STUDENT).fullName("Lý Quốc Huy").gender(Gender.MALE).phone("0901234568")
                .dob(java.time.LocalDate.of(2002, 10, 20)).address("88 Hai Bà Trưng, Quận 1, TP.HCM").isActive(true).build());

        // Sinh viên Khoa Ngoại ngữ
        userRepository.save(User.builder()
                .username("dangthuylinh").email("dangthuylinh@uni.edu.vn").passwordHash(passwordEncoder.encode("123456"))
                .role(Role.STUDENT).fullName("Đặng Thùy Linh").gender(Gender.FEMALE).phone("0912345679")
                .dob(java.time.LocalDate.of(2003, 2, 28)).address("42 Phan Xích Long, Quận Phú Nhuận, TP.HCM").isActive(true).build());

        userRepository.save(User.builder()
                .username("buituananh").email("buituananh@uni.edu.vn").passwordHash(passwordEncoder.encode("123456"))
                .role(Role.STUDENT).fullName("Bùi Tuấn Anh").gender(Gender.MALE).phone("0923456790")
                .dob(java.time.LocalDate.of(2003, 6, 5)).address("77 Nguyễn Đình Chiểu, Quận 3, TP.HCM").isActive(true).build());

        log.info("Đã tạo 13 sinh viên mẫu");
    }
}
