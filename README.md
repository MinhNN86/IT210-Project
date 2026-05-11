# Smart Academic & Lab Support Platform (IT210 Project)

## Tổng quan dự án

Đây là hệ thống ứng dụng Web hỗ trợ quá trình **cố vấn học thuật** và **quản lý trang thiết bị thực hành** cho sinh viên, giảng viên và quản trị viên.

Ứng dụng được xây dựng trên nền tảng **Java Spring Boot 4.x**, theo kiến trúc **Monolithic 3 lớp** (Controller → Service → Repository), sử dụng **Thymeleaf** làm template engine server-side rendering.

### Các chức năng chính
- **Đăng nhập / Đăng ký:** Xác thực JWT qua HttpOnly Cookie, mã hóa mật khẩu BCrypt.
- **Sinh viên:** Đặt lịch tư vấn học thuật, xem/hủy lịch, xem lịch sử đánh giá học tập.
- **Giảng viên:** Xem danh sách sinh viên đang chờ, đánh giá năng lực và tạo phiếu mượn thiết bị.
- **Quản trị viên:** CRUD thiết bị, duyệt/từ chối phiếu mượn thiết bị, quản lý kho.
- **Hồ sơ cá nhân:** Xem và cập nhật thông tin cá nhân (cho mọi vai trò).

---

## Công nghệ sử dụng

| Thành phần | Công nghệ |
|---|---|
| **Backend** | Java 21, Spring Boot 4.0.6 (Web, Data JPA, Validation) |
| **Database** | MySQL 8.x (Hibernate DDL auto-update) |
| **Frontend** | Thymeleaf, HTML5, Tailwind CSS (Glassmorphism UI) |
| **Bảo mật** | JWT (jjwt 0.12.6) qua HttpOnly Cookie + BCrypt (spring-security-crypto) |
| **Build Tool** | Gradle |
| **Utility** | Lombok (giảm boilerplate code) |

> **Lưu ý:** Dự án **không sử dụng Spring Security** toàn bộ — bảo mật được xử lý thủ công thông qua `JwtFilter` + `AuthInterceptor`, chỉ dùng thư viện `spring-security-crypto` cho BCrypt.

---

## Cấu trúc CSDL (Entity Relationship)

```
users (1) ──── (N) mentoring_sessions (N) ──── (1) lecturers
  │                    │                            │
  │                    │                            └── (1) departments
  │                    │
  │                    ├── (1) academic_evaluations
  │                    │
  │                    └── (1) borrowing_records
  │                              │
  │                              └── (N) borrowing_details (N) ──── equipments
  │
  └── (1) lecturers (bảng mở rộng thông tin giảng viên)
```

---

## Cấu trúc thư mục mã nguồn & Giải thích chi tiết từng file

### `src/main/java/com/projectit210/`

#### 📁 `config/` — Cấu hình ứng dụng

| File | Chức năng |
|---|---|
| [`AppConfig.java`](src/main/java/com/projectit210/config/AppConfig.java) | Lớp `@Configuration` chung, hiện tại trống (PasswordEncoder Bean đã chuyển sang `PasswordEncoderConfig`). |
| [`AuthInterceptor.java`](src/main/java/com/projectit210/config/AuthInterceptor.java) | `HandlerInterceptor` kiểm soát truy cập theo vai trò. Đọc `currentUser` từ request attribute (do `JwtFilter` set), kiểm tra URL path: `/student/**` → chỉ STUDENT, `/lecturer/**` → chỉ LECTURER, `/admin/**` → chỉ ADMIN. Chưa đăng nhập thì redirect về `/auth/login`. |
| [`DataInitializer.java`](src/main/java/com/projectit210/config/DataInitializer.java) | `CommandLineRunner` tự động khởi tạo dữ liệu mẫu khi chạy lần đầu (kiểm tra `departmentRepository.count() == 0`): tạo 5 Khoa (CNTT, DTVT, CK, KT, NN), 1 Admin (`admin/admin123`), 6 thiết bị mẫu, 3 giảng viên mẫu với mật khẩu `123456`. |
| [`WebConfig.java`](src/main/java/com/projectit210/config/WebConfig.java) | `WebMvcConfigurer` — đăng ký `AuthInterceptor` vào `InterceptorRegistry` (áp dụng `/**`, loại trừ CSS/JS/images), cấu hình `ResourceHandler` cho static files. |
| [`GlobalModelAttribute.java`](src/main/java/com/projectit210/config/GlobalModelAttribute.java) | `@ControllerAdvice` thêm attribute `currentUri` (URL hiện tại) vào mọi template Thymeleaf, dùng để highlight menu active trên sidebar. |
| [`ModelMapperConfig.java`](src/main/java/com/projectit210/config/ModelMapperConfig.java) | File trống — dự phòng, dự án dùng manual mapper thay vì ModelMapper library. |
| [`SwaggerConfig.java`](src/main/java/com/projectit210/config/SwaggerConfig.java) | File trống — dự phòng, ứng dụng Thymeleaf không cần Swagger API docs. |

---

#### 📁 `constant/` — Hằng số dùng chung

| File | Chức năng |
|---|---|
| [`ApiPath.java`](src/main/java/com/projectit210/constant/ApiPath.java) | Định nghĩa tất cả URL path dưới dạng `static final String` (vd: `LOGIN = "/auth/login"`, `STUDENT_BOOK = "/student/book"`, `ADMIN_EQUIPMENTS = "/admin/equipments"`). Tránh hardcode string trong controller. |
| [`AppConstant.java`](src/main/java/com/projectit210/constant/AppConstant.java) | Hằng số nghiệp vụ: `CURRENT_USER = "currentUser"` (key lưu user trong request attribute), `CANCEL_HOURS_BEFORE = 24` (phải hủy trước 24h), các mức đánh giá `PERFORMANCE_EXCELLENT`, `PERFORMANCE_GOOD`, v.v. |
| [`SecurityConstant.java`](src/main/java/com/projectit210/constant/SecurityConstant.java) | Hằng số bảo mật dự phòng: `TOKEN_COOKIE_NAME = "token"`, `BEARER_PREFIX = "Bearer "`. |

---

#### 📁 `controller/` — Xử lý HTTP Request & Điều hướng View

| File | Chức năng |
|---|---|
| [`AuthController.java`](src/main/java/com/projectit210/controller/AuthController.java) | Xử lý `/auth/**`: **GET/POST `/login`** — hiển thị form & xác thực đăng nhập, tạo JWT token lưu vào HttpOnly Cookie (24h), redirect theo role; **GET/POST `/register`** — hiển thị form & đăng ký tài khoản mới (chỉ đăng ký Student); **GET `/logout`** — xóa Cookie, redirect về login. |
| [`StudentController.java`](src/main/java/com/projectit210/controller/StudentController.java) | Xử lý `/student/**`: **GET `/dashboard`** — trang chủ sinh viên, hiển thị danh sách session; **GET `/book`** — form đặt lịch tư vấn; **POST `/book`** — tạo lịch mới (kiểm tra xung đột slot); **GET `/sessions`** — danh sách lịch đã đặt; **POST `/sessions/{id}/cancel`** — hủy lịch (phải trước 24h); **GET `/academic-history`** — lịch sử đánh giá học tập; **GET `/api/lecturers`** — AJAX API lấy giảng viên theo khoa; **GET `/api/booked-slots`** — AJAX API lấy slot đã đặt. |
| [`LecturerController.java`](src/main/java/com/projectit210/controller/LecturerController.java) | Xử lý `/lecturer/**`: **GET `/dashboard`** — trang chủ giảng viên, hiển thị sessions chờ xử lý; **GET `/pending-sessions`** — danh sách sinh viên đang chờ đánh giá; **GET `/evaluate/{sessionId}`** — form đánh giá năng lực + chọn thiết bị mượn; **POST `/evaluate`** — lưu đánh giá và tự động tạo phiếu mượn thiết bị. |
| [`AdminController.java`](src/main/java/com/projectit210/controller/AdminController.java) | Xử lý `/admin/**`: **GET `/dashboard`** — trang chủ admin, thống kê số thiết bị & phiếu mượn chờ xử lý; **CRUD thiết bị** — `/equipments` (danh sách), `/equipments/new` (thêm), `/equipments/{id}/edit` (sửa), `/equipments/{id}/delete` (xóa); **Quản lý mượn** — `/borrowings` (danh sách), `/borrowings/{id}/approve` (xác nhận xuất kho → trừ tồn kho), `/borrowings/{id}/reject` (từ chối). |
| [`ProfileController.java`](src/main/java/com/projectit210/controller/ProfileController.java) | Xử lý `/profile`: **GET** — hiển thị trang hồ sơ cá nhân (tự động chọn view theo role: `student/profile`, `lecturer/profile`, `admin/profile`); **POST** — cập nhật thông tin cá nhân (họ tên, SĐT, giới tính, ngày sinh, địa chỉ). |
| [`MentoringSessionController.java`](src/main/java/com/projectit210/controller/MentoringSessionController.java) | Controller trang chủ: **GET `/`** — đọc user từ request, redirect về dashboard theo role (chưa đăng nhập → login); **GET `/error`** — hiển thị trang lỗi chung. |
| [`BorrowingController.java`](src/main/java/com/projectit210/controller/BorrowingController.java) | File trống — chức năng mượn thiết bị đã tích hợp vào `AdminController`. |
| [`EquipmentController.java`](src/main/java/com/projectit210/controller/EquipmentController.java) | File trống — chức năng quản lý thiết bị đã tích hợp vào `AdminController`. |

---

#### 📁 `entity/` — Ánh xạ CSDL (JPA Entity)

| File | Bảng CSDL | Chức năng |
|---|---|---|
| [`User.java`](src/main/java/com/projectit210/entity/User.java) | `users` | Thông tin tài khoản: id (UUID), username, email, passwordHash, role (ADMIN/LECTURER/STUDENT), fullName, phone, gender, dob, avatarUrl, address, isActive, createdAt, updatedAt. Dùng `@PrePersist`/`@PreUpdate` tự động set timestamp. |
| [`Lecturer.java`](src/main/java/com/projectit210/entity/Lecturer.java) | `lecturers` | Bảng mở rộng cho giảng viên: quan hệ `@OneToOne` với `User`, `@ManyToOne` với `Department`. Lưu academicRank (Tiến sĩ, Thạc sĩ...) và specialization (chuyên môn). |
| [`Department.java`](src/main/java/com/projectit210/entity/Department.java) | `departments` | Khoa/Ngành: id, code (CNTT, DTVT...), name. Dùng làm dữ liệu nền (seed data). |
| [`Equipment.java`](src/main/java/com/projectit210/entity/Equipment.java) | `equipments` | Danh mục thiết bị thực hành: code, name, description, quantityInStock (số lượng tồn kho), minimumStock (ngưỡng cảnh báo), isActive, timestamps. |
| [`MentoringSession.java`](src/main/java/com/projectit210/entity/MentoringSession.java) | `mentoring_sessions` | Lịch hẹn tư vấn — bảng trung tâm: `@ManyToOne` student & lecturer, sessionDate, startTime, endTime, status (PENDING/CONFIRMED/COMPLETED/CANCELLED), note, cancelledAt. Có `@UniqueConstraint(lecturer_id, session_date, start_time)` chống trùng slot. |
| [`AcademicEvaluation.java`](src/main/java/com/projectit210/entity/AcademicEvaluation.java) | `academic_evaluations` | Kết quả đánh giá sau buổi tư vấn: `@OneToOne` MentoringSession, performanceLevel (Xuất sắc/Giỏi/Khá...), evaluationComment, recommendation. |
| [`BorrowingRecord.java`](src/main/java/com/projectit210/entity/BorrowingRecord.java) | `borrowing_records` | Phiếu mượn thiết bị tổng thể: `@OneToOne` MentoringSession, student, approvedBy (Admin), status (PENDING_DISPATCH/APPROVED/RETURNED...), borrowDate, expectedReturnDate, actualReturnDate. Có `@OneToMany` BorrowingDetail. |
| [`BorrowingDetail.java`](src/main/java/com/projectit210/entity/BorrowingDetail.java) | `borrowing_details` | Chi tiết phiếu mượn — quan hệ N-N giữa BorrowingRecord và Equipment: mỗi record chứa borrowingRecord, equipment, quantity (số lượng mượn của từng thiết bị). |

---

#### 📁 `enums/` — Định nghĩa tập giá trị cố định

| File | Giá trị | Ý nghĩa |
|---|---|---|
| [`Role.java`](src/main/java/com/projectit210/enums/Role.java) | `ADMIN`, `LECTURER`, `STUDENT` | Vai trò người dùng trong hệ thống. |
| [`SessionStatus.java`](src/main/java/com/projectit210/enums/SessionStatus.java) | `PENDING`, `CONFIRMED`, `COMPLETED`, `CANCELLED` | Trạng thái buổi tư vấn: Chờ xác nhận → Đã xác nhận → Đã hoàn thành / Đã hủy. |
| [`BorrowStatus.java`](src/main/java/com/projectit210/enums/BorrowStatus.java) | `PENDING_DISPATCH`, `APPROVED`, `REJECTED`, `RETURNED` | Trạng thái phiếu mượn: Chờ cấp phát → Đã duyệt / Từ chối → Đã trả. |
| [`Gender.java`](src/main/java/com/projectit210/enums/Gender.java) | `MALE`, `FEMALE`, `OTHER` | Giới tính người dùng. |

---

#### 📁 `dto/request/` — Dữ liệu gửi lên từ Client (Form Submission)

| File | Chức năng |
|---|---|
| [`LoginRequest.java`](src/main/java/com/projectit210/dto/request/LoginRequest.java) | Dữ liệu form đăng nhập: `username`, `password`. Có annotation `@NotBlank` validation. |
| [`RegisterRequest.java`](src/main/java/com/projectit210/dto/request/RegisterRequest.java) | Dữ liệu form đăng ký: `username`, `email`, `password`, `fullName`, `phone`, `gender`, `departmentId`. Có validation annotation. |
| [`CreateSessionRequest.java`](src/main/java/com/projectit210/dto/request/CreateSessionRequest.java) | Dữ liệu form đặt lịch tư vấn: `lecturerId`, `sessionDate`, `startTime`, `endTime`, `note`. Có validation kiểm tra ngày phải trong tương lai. |
| [`EvaluationRequest.java`](src/main/java/com/projectit210/dto/request/EvaluationRequest.java) | Dữ liệu form đánh giá: `sessionId`, `performanceLevel`, `evaluationComment`, `recommendation`, danh sách `equipmentIds` và `quantities` (cho phiếu mượn kèm theo). |
| [`EquipmentRequest.java`](src/main/java/com/projectit210/dto/request/EquipmentRequest.java) | Dữ liệu form thêm/sửa thiết bị: `code`, `name`, `description`, `quantityInStock`, `minimumStock`. |
| [`BorrowRequest.java`](src/main/java/com/projectit210/dto/request/BorrowRequest.java) | Dữ liệu yêu cầu mượn thiết bị (dùng nội bộ trong EvaluationRequest). |
| [`ProfileUpdateRequest.java`](src/main/java/com/projectit210/dto/request/ProfileUpdateRequest.java) | Dữ liệu form cập nhật hồ sơ: `fullName`, `phone`, `gender`, `dob`, `address`. |

---

#### 📁 `dto/response/` — Dữ liệu đóng gói gửi về Client

| File | Chức năng |
|---|---|
| [`AuthResponse.java`](src/main/java/com/projectit210/dto/response/AuthResponse.java) | Thông tin trả về sau đăng nhập thành công: token, username, role. |
| [`SessionResponse.java`](src/main/java/com/projectit210/dto/response/SessionResponse.java) | Thông tin buổi tư vấn để hiển thị: tên sinh viên, tên giảng viên, ngày, giờ, trạng thái, ghi chú. |
| [`EquipmentResponse.java`](src/main/java/com/projectit210/dto/response/EquipmentResponse.java) | Thông tin thiết bị: id, code, name, quantityInStock, minimumStock, isActive. |
| [`AcademicHistoryResponse.java`](src/main/java/com/projectit210/dto/response/AcademicHistoryResponse.java) | Lịch sử đánh giá học tập (JOIN data): thông tin session, tên giảng viên, performanceLevel, comment, recommendation, thông tin phiếu mượn liên quan. |
| [`BorrowingResponse.java`](src/main/java/com/projectit210/dto/response/BorrowingResponse.java) | Thông tin phiếu mượn: tên sinh viên, tên thiết bị, số lượng, trạng thái, ngày mượn, ngày trả dự kiến. |

---

#### 📁 `mapper/` — Chuyển đổi Entity ↔ DTO

| File | Chức năng |
|---|---|
| [`UserMapper.java`](src/main/java/com/projectit210/mapper/UserMapper.java) | Chuyển đổi `User` entity thành các DTO liên quan (response, profile). |
| [`EquipmentMapper.java`](src/main/java/com/projectit210/mapper/EquipmentMapper.java) | Chuyển đổi `Equipment` entity ↔ `EquipmentRequest`/`EquipmentResponse`. |
| [`SessionMapper.java`](src/main/java/com/projectit210/mapper/SessionMapper.java) | Chuyển đổi `MentoringSession` entity → `SessionResponse` (bao gồm tên student, lecturer). |
| [`BorrowingMapper.java`](src/main/java/com/projectit210/mapper/BorrowingMapper.java) | Chuyển đổi `BorrowingRecord` + `BorrowingDetail` → `BorrowingResponse` (JOIN phức tạp, tổng hợp thông tin thiết bị). |

---

#### 📁 `repository/` — Giao tiếp CSDL (Spring Data JPA)

| File | Chức năng |
|---|---|
| [`UserRepository.java`](src/main/java/com/projectit210/repository/UserRepository.java) | CRUD bảng `users`. Custom methods: `findByUsername()`, `findByEmail()`, `findByRole()`. |
| [`LecturerRepository.java`](src/main/java/com/projectit210/repository/LecturerRepository.java) | CRUD bảng `lecturers`. Custom methods: `findByUserId()` (tìm lecturer profile từ user), `findByDepartmentIdWithDetails()` (JOIN FETCH để lấy thông tin user + department). |
| [`DepartmentRepository.java`](src/main/java/com/projectit210/repository/DepartmentRepository.java) | CRUD bảng `departments`. Custom method: `findByCode()` (tìm khoa theo mã CNTT, DTVT...). |
| [`EquipmentRepository.java`](src/main/java/com/projectit210/repository/EquipmentRepository.java) | CRUD bảng `equipments`. Custom method: `findByIsActiveTrue()` (chỉ lấy thiết bị đang hoạt động). |
| [`MentoringSessionRepository.java`](src/main/java/com/projectit210/repository/MentoringSessionRepository.java) | CRUD bảng `mentoring_sessions`. Custom queries: tìm session theo student, theo lecturer + status, kiểm tra slot trùng (conflict detection), lấy booked slots trong ngày. |
| [`AcademicEvaluationRepository.java`](src/main/java/com/projectit210/repository/AcademicEvaluationRepository.java) | CRUD bảng `academic_evaluations`. Custom queries: tìm evaluation theo session, lấy lịch sử đánh giá của sinh viên (JOIN session + lecturer + borrowing). |
| [`BorrowingRecordRepository.java`](src/main/java/com/projectit210/repository/BorrowingRecordRepository.java) | CRUD bảng `borrowing_records`. Custom queries: tìm theo status (PENDING_DISPATCH), tìm theo student, tìm tất cả sắp xếp theo ngày tạo. |
| [`BorrowingDetailRepository.java`](src/main/java/com/projectit210/repository/BorrowingDetailRepository.java) | CRUD bảng `borrowing_details`. Custom method: `findByBorrowingRecordId()` lấy chi tiết thiết bị của một phiếu mượn. |

---

#### 📁 `security/` — Cơ chế bảo mật & Xác thực

| File | Chức năng |
|---|---|
| [`JwtFilter.java`](src/main/java/com/projectit210/security/JwtFilter.java) | `OncePerRequestFilter` chạy trước mọi request: đọc JWT token từ HttpOnly Cookie → validate → extract userId → load User từ DB → set vào request attribute (`currentUser`, `currentUserId`). Bỏ qua filter cho static resources (CSS/JS/images). |
| [`JwtUtil.java`](src/main/java/com/projectit210/security/JwtUtil.java) | Tiện ích JWT: `generateToken(userId)` tạo token với subject là userId, thời hạn 24h; `extractUserId(token)` parse claims; `validateToken(token)` kiểm tra hợp lệ. Dùng HMAC-SHA với secret key từ `application.properties`. |
| [`PasswordEncoderConfig.java`](src/main/java/com/projectit210/security/PasswordEncoderConfig.java) | `@Configuration` khai báo Bean `BCryptPasswordEncoder`. Dùng để hash mật khẩu khi đăng ký và so khớp khi đăng nhập. |
| [`SecurityConfig.java`](src/main/java/com/projectit210/security/SecurityConfig.java) | File trống — dự phòng. Bảo mật được xử lý bởi `JwtFilter` + `AuthInterceptor`, không dùng Spring Security starter. |
| [`CustomUserDetailsService.java`](src/main/java/com/projectit210/security/CustomUserDetailsService.java) | File trống — dự phòng cho phần mở rộng tích hợp Spring Security sau này. |

---

#### 📁 `service/` — Logic nghiệp vụ (Business Logic)

| File (Interface) | Chức năng |
|---|---|
| [`AuthService.java`](src/main/java/com/projectit210/service/AuthService.java) | Interface xác thực: `register()` — đăng ký tài khoản mới (hash BCrypt); `login()` — xác thực username/password. |
| [`UserService.java`](src/main/java/com/projectit210/service/UserService.java) | Interface quản lý user: `findById()`, `findByUsername()`, `findAll()`, `findByRole()`. |
| [`EquipmentService.java`](src/main/java/com/projectit210/service/EquipmentService.java) | Interface quản lý thiết bị: `findAll()`, `findAllActive()`, `findById()`, `findEntityById()`, `create()`, `update()`, `delete()`. |
| [`MentoringSessionService.java`](src/main/java/com/projectit210/service/MentoringSessionService.java) | Interface quản lý lịch tư vấn: `createSession()` — đặt lịch (kiểm tra xung đột slot, ngày quá khứ); `cancelSession()` — hủy lịch (kiểm tra >= 24h trước); `getSessionsByStudent()`, `getPendingSessionsByLecturer()`, `getBookedSlots()`. |
| [`AcademicEvaluationService.java`](src/main/java/com/projectit210/service/AcademicEvaluationService.java) | Interface đánh giá học tập: `completeEvaluation()` — transaction hoàn tất đánh giá + tự động tạo phiếu mượn thiết bị; `getAcademicHistory()` — lấy lịch sử đánh giá (JOIN data). |
| [`BorrowingService.java`](src/main/java/com/projectit210/service/BorrowingService.java) | Interface quản lý mượn thiết bị: `approveDispatch()` — xác nhận xuất kho (kiểm tra tồn kho, trừ stock) trong transaction; `rejectBorrowing()`, `getPendingDispatch()`, `getAllBorrowings()`, `getByStudent()`. |
| [`InventoryService.java`](src/main/java/com/projectit210/service/InventoryService.java) | Interface quản lý tồn kho: `checkStock()` — kiểm tra đủ hàng; `reduceStock()` — trừ tồn kho khi xuất; `restoreStock()` — hoàn trả khi hủy/từ chối. |
| [`ProfileService.java`](src/main/java/com/projectit210/service/ProfileService.java) | Interface hồ sơ cá nhân: `getProfile()`, `updateProfile()`. |

---

#### 📁 `service/impl/` — Triển khai thực tế Service

| File | Chức năng |
|---|---|
| [`AuthServiceImpl.java`](src/main/java/com/projectit210/service/impl/AuthServiceImpl.java) | Triển khai `AuthService`: kiểm tra trùng username/email, hash mật khẩu bằng BCrypt, tạo User entity, nếu đăng ký Student thì tự động set role. |
| [`UserServiceImpl.java`](src/main/java/com/projectit210/service/impl/UserServiceImpl.java) | Triển khai `UserService`: wrapper gọi `UserRepository`. |
| [`EquipmentServiceImpl.java`](src/main/java/com/projectit210/service/impl/EquipmentServiceImpl.java) | Triển khai `EquipmentService`: CRUD thiết bị, kiểm tra trùng code, chuyển đổi Entity ↔ DTO qua `EquipmentMapper`, soft delete (set `isActive = false`). |
| [`MentoringSessionServiceImpl.java`](src/main/java/com/projectit210/service/impl/MentoringSessionServiceImpl.java) | Triển khai `MentoringSessionService`: logic phức tạp nhất — kiểm tra ngày đặt phải trong tương lai, kiểm tra xung đột slot (unique constraint + query), kiểm tra hủy lịch phải trước 24h (`CANCEL_HOURS_BEFORE`), chuyển đổi qua `SessionMapper`. |
| [`AcademicEvaluationServiceImpl.java`](src/main/java/com/projectit210/service/impl/AcademicEvaluationServiceImpl.java) | Triển khai `AcademicEvaluationService`: **@Transactional** — trong một transaction: cập nhật session status → COMPLETED, tạo `AcademicEvaluation`, tạo `BorrowingRecord` + danh sách `BorrowingDetail` nếu có thiết bị mượn. |
| [`BorrowingServiceImpl.java`](src/main/java/com/projectit210/service/impl/BorrowingServiceImpl.java) | Triển khai `BorrowingService`: **@Transactional** — approveDispatch kiểm tra tồn kho qua `InventoryService`, trừ stock, cập nhật status; rejectBorrowing hoàn trả stock nếu cần. |
| [`InventoryServiceImpl.java`](src/main/java/com/projectit210/service/impl/InventoryServiceImpl.java) | Triển khai `InventoryService`: kiểm tra `quantityInStock >= quantity`, trừ/cộng số lượng tồn kho, throw `BadRequestException` nếu không đủ hàng. |
| [`ProfileServiceImpl.java`](src/main/java/com/projectit210/service/impl/ProfileServiceImpl.java) | Triển khai `ProfileService`: lấy thông tin user theo id, cập nhật các trường (fullName, phone, gender, dob, address) và lưu. |

---

#### 📁 `exception/` — Xử lý ngoại lệ

| File | Chức năng |
|---|---|
| [`GlobalExceptionHandler.java`](src/main/java/com/projectit210/exception/GlobalExceptionHandler.java) | `@ControllerAdvice` bắt mọi exception: `BadRequestException` → redirect `/error`, `ResourceNotFoundException` → redirect `/error`, `ConflictException` → redirect `/error`, `UnauthorizedException` → redirect `/auth/login`, `Exception` (fallback) → redirect `/error`. Tất cả kèm flash message. |
| [`BadRequestException.java`](src/main/java/com/projectit210/exception/BadRequestException.java) | Exception tùy biến — ném khi dữ liệu đầu vào không hợp lệ (vd: trùng username, slot đã đặt, không đủ tồn kho). |
| [`ResourceNotFoundException.java`](src/main/java/com/projectit210/exception/ResourceNotFoundException.java) | Exception tùy biến — ném khi không tìm thấy bản ghi (vd: sessionId không tồn tại). |
| [`ConflictException.java`](src/main/java/com/projectit210/exception/ConflictException.java) | Exception tùy biến — ném khi có xung đột dữ liệu (vd: trùng slot tư vấn). |
| [`UnauthorizedException.java`](src/main/java/com/projectit210/exception/UnauthorizedException.java) | Exception tùy biến — ném khi không có quyền truy cập. |

---

#### 📁 `validation/` — Custom Validator

| File | Chức năng |
|---|---|
| [`FutureDateValidator.java`](src/main/java/com/projectit210/validation/FutureDateValidator.java) | Validator tùy chỉnh kiểm tra ngày đặt lịch phải ở tương lai (không được đặt lịch trong quá khứ). |
| [`UniqueEmailValidator.java`](src/main/java/com/projectit210/validation/UniqueEmailValidator.java) | Validator tùy chỉnh kiểm tra email chưa tồn tại trong hệ thống (tránh trùng lặp khi đăng ký). |
| [`BookingSlotValidator.java`](src/main/java/com/projectit210/validation/BookingSlotValidator.java) | Validator tùy chỉnh kiểm tra slot thời gian hợp lệ (thời gian bắt đầu phải trước thời gian kết thúc). |

---

#### 📁 `util/` — Tiện ích hỗ trợ

| File | Chức năng |
|---|---|
| [`DateUtil.java`](src/main/java/com/projectit210/util/DateUtil.java) | Định dạng ngày tháng: `formatDate()` (dd/MM/yyyy), `formatTime()` (HH:mm), `formatDateTime()` (dd/MM/yyyy HH:mm), `isPast()` kiểm tra ngày giờ trong quá khứ. |
| [`SecurityUtil.java`](src/main/java/com/projectit210/util/SecurityUtil.java) | Tiện ích bảo mật: lấy thông tin user hiện tại từ request attribute (wrapper quanh `request.getAttribute(AppConstant.CURRENT_USER)`). |
| [`JwtUtil.java`](src/main/java/com/projectit210/util/JwtUtil.java) | File trống — JWT utility thực tế nằm trong `security/JwtUtil.java`. |
| [`FileUploadUtil.java`](src/main/java/com/projectit210/util/FileUploadUtil.java) | File trống — dự phòng cho chức năng upload avatar. |

---

### `src/main/resources/`

#### Tệp cấu hình

| File | Chức năng |
|---|---|
| [`application.properties`](src/main/resources/application.properties) | Cấu hình Spring Boot: kết nối MySQL (`localhost:3306/smart_academic_platform`), Hibernate DDL auto-update, Thymeleaf (tắt cache khi dev), JWT secret key + thời hạn 24h + cookie name, cấu hình SQL init. |
| [`init.sql`](src/main/resources/init.sql) | Script SQL tạo cấu trúc database thô ban đầu (dự phòng, hiện dùng Hibernate auto DDL). |

#### 📁 `static/css/`

| File | Chức năng |
|---|---|
| [`style.css`](src/main/resources/static/css/style.css) | File chứa các tùy chỉnh CSS mở rộng. Giao diện chính của hệ thống đã được chuyển sang sử dụng **Tailwind CSS** (qua CDN) với phong cách **Glassmorphism** hiện đại. |

#### 📁 `templates/` — Giao diện Thymeleaf (HTML)

##### `templates/auth/` — Trang xác thực

| File | Chức năng |
|---|---|
| [`login.html`](src/main/resources/templates/auth/login.html) | Trang đăng nhập: form username/password, hiển thị lỗi nếu sai thông tin, link đến đăng ký. |
| [`register.html`](src/main/resources/templates/auth/register.html) | Trang đăng ký: form đầy đủ (username, email, password, fullName, phone, gender, department), validation, dropdown danh sách Khoa. |
| [`forgot-password.html`](src/main/resources/templates/auth/forgot-password.html) | Trang quên mật khẩu (placeholder, chưa triển khai logic). |

##### `templates/student/` — Giao diện Sinh viên

| File | Chức năng |
|---|---|
| [`dashboard.html`](src/main/resources/templates/student/dashboard.html) | Trang chủ sinh viên: hiển thị welcome message, danh sách session gần đây, thống kê nhanh. |
| [`booking-form.html`](src/main/resources/templates/student/booking-form.html) | Form đặt lịch tư vấn: dropdown chọn Khoa → AJAX load giảng viên → chọn ngày → AJAX load slot trống → chọn giờ + ghi chú. |
| [`my-sessions.html`](src/main/resources/templates/student/my-sessions.html) | Danh sách lịch đã đặt: bảng hiển thị ngày, giờ, giảng viên, trạng thái, nút hủy (chỉ hiện khi >= 24h trước). |
| [`academic-history.html`](src/main/resources/templates/student/academic-history.html) | Lịch sử đánh giá học tập: bảng hiển thị buổi tư vấn, nhận xét của giảng viên, mức đánh giá, đề xuất, thiết bị đã mượn. |
| [`profile.html`](src/main/resources/templates/student/profile.html) | Trang hồ sơ cá nhân: hiển thị và chỉnh sửa thông tin cá nhân. |

##### `templates/lecturer/` — Giao diện Giảng viên

| File | Chức năng |
|---|---|
| [`dashboard.html`](src/main/resources/templates/lecturer/dashboard.html) | Trang chủ giảng viên: hiển thị thông tin cá nhân, danh sách session chờ xử lý. |
| [`pending-sessions.html`](src/main/resources/templates/lecturer/pending-sessions.html) | Danh sách sinh viên đang chờ đánh giá: bảng hiển thị tên sinh viên, ngày, ghi chú, nút "Đánh giá". |
| [`evaluation-form.html`](src/main/resources/templates/lecturer/evaluation-form.html) | Form đánh giá năng lực: chọn mức đánh giá (Xuất sắc → Yếu), nhận xét, đề xuất, chọn thiết bị mượn kèm số lượng. |
| [`profile.html`](src/main/resources/templates/lecturer/profile.html) | Trang hồ sơ cá nhân giảng viên. |

##### `templates/admin/` — Giao diện Quản trị viên

| File | Chức năng |
|---|---|
| [`dashboard.html`](src/main/resources/templates/admin/dashboard.html) | Trang chủ admin: thống kê tổng số thiết bị, số phiếu mượn chờ xử lý, danh sách nhanh. |
| [`equipments.html`](src/main/resources/templates/admin/equipments.html) | Danh sách thiết bị: bảng hiển thị code, tên, tồn kho, ngưỡng tối thiểu, trạng thái, nút sửa/xóa. |
| [`equipment-form.html`](src/main/resources/templates/admin/equipment-form.html) | Form thêm/sửa thiết bị: dùng chung cho create và edit (biến `isEdit` phân biệt). |
| [`borrowing-management.html`](src/main/resources/templates/admin/borrowing-management.html) | Quản lý phiếu mượn: bảng danh sách tất cả phiếu, nút "Xác nhận xuất kho" / "Từ chối" cho phiếu chờ xử lý. |
| [`reports.html`](src/main/resources/templates/admin/reports.html) | Trang báo cáo thống kê (placeholder, chưa triển khai đầy đủ). |
| [`profile.html`](src/main/resources/templates/admin/profile.html) | Trang hồ sơ cá nhân admin. |

##### `templates/fragments/` — Thymeleaf Fragments (thành phần tái sử dụng)

| File | Chức năng |
|---|---|
| [`header.html`](src/main/resources/templates/fragments/header.html) | Fragment header: hiển thị tên ứng dụng, thông tin user đăng nhập, nút đăng xuất, và cấu hình **Tailwind CSS CDN**. |
| [`sidebar.html`](src/main/resources/templates/fragments/sidebar.html) | Fragment sidebar: menu điều hướng thay đổi theo role (Student: Dashboard, Đặt lịch, Lịch sử; Lecturer: Dashboard, Chờ xử lý; Admin: Dashboard, Thiết bị, Mượn thiết bị). Highlight menu active dựa trên `currentUri`. |
| [`footer.html`](src/main/resources/templates/fragments/footer.html) | Fragment footer: thông tin bản quyền. |

##### `templates/layouts/` — Layout Thymeleaf

| File | Chức năng |
|---|---|
| [`main-layout.html`](src/main/resources/templates/layouts/main-layout.html) | Layout chính: cấu trúc HTML cơ bản (head, body) bao gồm header, sidebar, content area, footer. Các trang cụ thể inject content vào layout. |

##### Khác

| File | Chức năng |
|---|---|
| [`error.html`](src/main/resources/templates/error.html) | Trang lỗi chung: hiển thị thông báo lỗi từ flash attribute. |

---

### Tệp cấu hình build

| File | Chức năng |
|---|---|
| [`build.gradle`](build.gradle) | Cấu hình Gradle: Spring Boot 4.0.6, Java 21, dependencies (JPA, Thymeleaf, Validation, JWT jjwt 0.12.6, BCrypt, Lombok, MySQL connector, DevTools). |
| [`settings.gradle`](settings.gradle) | Tên project Gradle: `Project-IT210`. |

---

## Luồng xử lý Request

```
Browser Request
    │
    ▼
[JwtFilter] ─ Đọc Cookie → Validate JWT → Set currentUser vào request attribute
    │
    ▼
[AuthInterceptor] ─ Kiểm tra URL path vs Role → Chặn truy cập trái phép
    │
    ▼
[Controller] ─ Nhận request → Gọi Service → Trả về Thymeleaf View
    │
    ▼
[Service] ─ Xử lý business logic → Gọi Repository → Trả về DTO/Entity
    │
    ▼
[Repository] ─ Tương tác MySQL qua Spring Data JPA
    │
    ▼
MySQL Database
```

---

## Hướng dẫn chạy dự án

### Yêu cầu
- **JDK 21** trở lên
- **MySQL 8.x** chạy ở cổng `3306`
- **Gradle** (wrapper đã bao gồm trong project)

### Các bước
1. Khởi động MySQL và đảm bảo cổng `3306` khả dụng.
2. Database `smart_academic_platform` sẽ được tự động tạo (do cấu hình `createDatabaseIfNotExist=true`).
3. Mở terminal tại thư mục project và chạy:
   ```bash
   ./gradlew bootRun
   ```
4. Truy cập ứng dụng tại: `http://localhost:8080`
5. Dữ liệu mẫu sẽ tự động được khởi tạo khi chạy lần đầu.

### Tài khoản mặc định

| Vai trò | Username | Password |
|---|---|---|
| **Admin** | `admin` | `admin123` |
| **Giảng viên** | `nguyenvana` | `123456` |
| **Giảng viên** | `tranthib` | `123456` |
| **Giảng viên** | `levanc` | `123456` |

> Sinh viên cần tự đăng ký qua trang `/auth/register`.
