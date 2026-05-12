# Tài liệu Xác thực JWT (JSON Web Token) - Smart Academic Platform

## 1. Tổng quan

Hệ thống sử dụng **JWT (JSON Web Token)** làm cơ chế xác thực chính, lưu trữ token trong **HttpOnly Cookie** để bảo vệ chống lại các cuộc tấn công XSS. Hệ thống **không sử dụng Spring Security** — toàn bộ bảo mật được xử lý thủ công thông qua `JwtFilter` (Servlet Filter) + `AuthInterceptor` (HandlerInterceptor).

### Công nghệ sử dụng

| Thư viện | Phiên bản | Mục đích |
|----------|-----------|----------|
| `io.jsonwebtoken:jjwt-api` | 0.12.6 | JWT API interface |
| `io.jsonwebtoken:jjwt-impl` | 0.12.6 | JWT implementation |
| `io.jsonwebtoken:jjwt-jackson` | 0.12.6 | JWT Jackson serialization |
| `spring-security-crypto` | (managed) | BCrypt password hashing |
| Servlet API | Jakarta | Cookie, Filter, HttpServletRequest |

### Mô hình bảo mật

```
┌───────────────────────────────────────────────────────────────┐
│                    BẢO MẬT 3 LỚP                               │
│                                                                │
│  Layer 1: JwtFilter (Servlet Filter)                           │
│  → Đọc JWT từ Cookie → Validate → Set currentUser attribute   │
│  → Chạy cho MỌI request (trừ static resources)                │
│                                                                │
│  Layer 2: AuthInterceptor (HandlerInterceptor)                 │
│  → Kiểm tra currentUser != null                                │
│  → Kiểm tra Role phù hợp với URL path                          │
│  → Redirect nếu không có quyền                                 │
│                                                                │
│  Layer 3: Controller Logic                                     │
│  → Kiểm tra quyền sở hữu (ownership)                           │
│  → Kiểm tra trạng thái nghiệp vụ                               │
└───────────────────────────────────────────────────────────────┘
```

---

## 2. Cấu hình JWT

### 2.1 application.properties

```properties
# JWT Configuration
app.jwt.secret=SmartAcademicPlatformSecretKeyIT210ProjectJWT2026VeryLongSecretKey
app.jwt.expiration-ms=86400000
app.jwt.cookie-name=token
```

| Parameter | Giá trị | Mô tả |
|-----------|---------|--------|
| `app.jwt.secret` | `SmartAcademicPlatform...` | Khóa bí mật HMAC-SHA (≥256 bit cho HS256) |
| `app.jwt.expiration-ms` | `86400000` (24 giờ) | Thời gian sống của token (milliseconds) |
| `app.jwt.cookie-name` | `token` | Tên cookie chứa JWT |

### 2.2 Cookie Properties

| Property | Giá trị | Mô tả |
|----------|---------|--------|
| Name | `token` | Tên cookie |
| HttpOnly | `true` | JavaScript không thể đọc (chống XSS) |
| Path | `/` | Áp dụng cho mọi path |
| Max-Age | `86400` (24 giờ) | Thời gian sống cookie (seconds) |
| Secure | Không đặt | Chỉ dùng HTTPS trong production |

---

## 3. Cấu trúc JWT Token

### 3.1 Token Format

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTYiLCJpYXQiOjE3MDAwMDAwMDAsImV4cCI6MTcwMDA4NjQwMH0.signature
│                     │                                                                    │
│    HEADER           │    PAYLOAD                                                          │  SIGNATURE
│    (Base64)         │    (Base64)                                                        │  (HMAC-SHA)
```

### 3.2 JWT Header (tự động bởi jjwt)

```json
{
  "alg": "HS256"
}
```

| Field | Giá trị | Mô tả |
|-------|---------|--------|
| `alg` | `HS256` | Thuật toán HMAC-SHA256 |

### 3.3 JWT Payload (Claims)

```json
{
  "sub": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "iat": 1700000000,
  "exp": 1700086400
}
```

| Claim | Tên | Kiểu | Mô tả |
|-------|-----|------|--------|
| `sub` | Subject | String (UUID) | **User ID** — định danh duy nhất của user |
| `iat` | Issued At | Number (epoch seconds) | Thời gian tạo token |
| `exp` | Expiration | Number (epoch seconds) | Thời gian hết hạn token (= `iat` + 24h) |

**Lưu ý quan trọng:**
- Token **chỉ chứa `sub` (user ID)**, không chứa role, username hay thông tin khác
- Role và thông tin user được load từ database mỗi request (qua `JwtFilter`)
- Điều này đảm bảo: nếu user bị vô hiệu hóa (`isActive = false`) hoặc đổi role, thay đổi có hiệu lực ngay lập tức

---

## 4. Kiến trúc hệ thống

### 4.1 Sơ đồ lớp (Class Diagram)

```
┌─────────────────────────────────────────────────────────────────┐
│                     CONFIG / REGISTRATION                       │
│                                                                  │
│  WebConfig (implements WebMvcConfigurer)                         │
│  └── addInterceptors()                                          │
│      └── Đăng ký AuthInterceptor cho path /**                   │
│          (loại trừ /css/**, /js/**, /images/**, /webjars/**)    │
│                                                                  │
│  PasswordEncoderConfig (@Configuration)                          │
│  └── @Bean BCryptPasswordEncoder                                 │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                     SECURITY LAYER                               │
│                                                                  │
│  JwtUtil (@Component)                                            │
│  ├── generateToken(userId)     → Tạo JWT (sub=userId, exp=24h) │
│  ├── extractUserId(token)      → Parse claims → return sub      │
│  ├── validateToken(token)      → Parse claims → true/false      │
│  └── parseClaims(token)        → Jwts.parser().verifyWith(key)  │
│                                                                  │
│  JwtFilter (@Component, extends OncePerRequestFilter)            │
│  ├── doFilterInternal()                                          │
│  │   ├── extractTokenFromCookie(request)                         │
│  │   ├── jwtUtil.validateToken(token)                            │
│  │   ├── jwtUtil.extractUserId(token)                            │
│  │   ├── userRepository.findById(userId)                         │
│  │   └── request.setAttribute(CURRENT_USER, user)                │
│  └── shouldNotFilter()                                           │
│      └── Bỏ qua: /css/**, /js/**, /images/**, /webjars/**       │
│                                                                  │
│  AuthInterceptor (@Component, implements HandlerInterceptor)      │
│  ├── preHandle()                                                 │
│  │   ├── isPublicPath(path) → return true                       │
│  │   ├── currentUser == null → redirect /auth/login             │
│  │   ├── /student/** → chỉ STUDENT                              │
│  │   ├── /lecturer/** → chỉ LECTURER                            │
│  │   ├── /admin/** → chỉ ADMIN                                  │
│  │   └── /meeting/** → STUDENT hoặc LECTURER                    │
│  └── postHandle()                                                │
│      └── Thêm requestURI vào modelAndView                       │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                     CONTROLLER LAYER                             │
│                                                                  │
│  AuthController                                                  │
│  ├── POST /auth/login          → Xác thực → Generate JWT        │
│  ├── POST /auth/verify-2fa     → 2FA → Generate JWT             │
│  ├── GET  /auth/logout         → Xóa Cookie + Invalidate Session│
│  └── Các endpoints khác (2FA setup, register)                   │
│                                                                  │
│  * Mọi controller khác đọc currentUser từ request attribute      │
│    (đã được JwtFilter set trước đó)                              │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 Filter Chain Order

```
HTTP Request
    │
    ▼
┌──────────────────────┐
│   Servlet Container   │  (Tomcat)
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│   Spring Filter Chain │
│                       │
│  1. JwtFilter         │  ← OncePerRequestFilter
│     (tạo trước)       │     Đọc cookie, validate JWT
│                       │     Set currentUser attribute
│                       │
│  2. Các filters khác  │  (nếu có)
│                       │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│   DispatcherServlet  │  Spring MVC
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│   AuthInterceptor     │  ← HandlerInterceptor
│   (preHandle)         │     Kiểm tra role theo path
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│   Controller Method   │  Xử lý request
└──────────────────────┘
```

---

## 5. Luồng hoạt động chi tiết

### 5.1 Luồng Đăng nhập (Tạo JWT)

```
┌────────┐                    ┌────────────┐                ┌─────────────┐
│  User  │                    │   Server   │                │  Database   │
└───┬────┘                    └─────┬──────┘                └──────┬──────┘
    │                               │                              │
    │  1. POST /auth/login          │                              │
    │  {username, password}         │                              │
    │──────────────────────────────>│                              │
    │                               │                              │
    │                               │  2. JwtFilter chạy:          │
    │                               │     Không có cookie →        │
    │                               │     currentUser = null        │
    │                               │                              │
    │                               │  3. AuthInterceptor:         │
    │                               │     /auth/login = public     │
    │                               │     → Cho phép đi tiếp       │
    │                               │                              │
    │                               │  4. AuthController.login()   │
    │                               │     authService.login(request)│
    │                               │                              │
    │                               │  5. Tìm user by username     │
    │                               │─────────────────────────────>│
    │                               │<─────────────────────────────│
    │                               │                              │
    │                               │  6. Verify password          │
    │                               │     BCrypt.matches(          │
    │                               │       rawPassword, hash)     │
    │                               │                              │
    │                               │  7. Check isActive == true   │
    │                               │                              │
    │            ┌──────────────────┤                              │
    │            │ 2FA DISABLED     │  2FA ENABLED                 │
    │            │                  │                              │
    │            │  8a. Generate    │  8b. Lưu userId vào session  │
    │            │  JWT token       │      Redirect /verify-2fa    │
    │            │                  │      (xem docs 2FA)          │
    │            │                  │                              │
    │            │  9a. Tạo Cookie  │                              │
    │            │  - Name: token   │                              │
    │            │  - Value: JWT    │                              │
    │            │  - HttpOnly: true│                              │
    │            │  - Path: /       │                              │
    │            │  - MaxAge: 86400 │                              │
    │            │                  │                              │
    │            │  10a. Redirect   │                              │
    │            │  theo role       │                              │
    │            │                  │                              │
    │  Set-Cookie: token=eyJ...    │                              │
    │  Redirect to dashboard       │                              │
    │<─────────────────────────────┤                              │
```

### 5.2 Luồng Kiểm tra JWT (Mỗi Request)

```
┌────────┐                    ┌────────────┐                ┌─────────────┐
│  User  │                    │   Server   │                │  Database   │
└───┬────┘                    └─────┬──────┘                └──────┬──────┘
    │                               │                              │
    │  1. GET /student/dashboard    │                              │
    │  Cookie: token=eyJhbG...      │                              │
    │──────────────────────────────>│                              │
    │                               │                              │
    │                               │  2. JwtFilter.doFilterInternal()
    │                               │                              │
    │                               │  3. extractTokenFromCookie() │
    │                               │     → Đọc cookie "token"    │
    │                               │     → Giá trị: "eyJhbG..."  │
    │                               │                              │
    │                               │  4. jwtUtil.validateToken()  │
    │                               │     ├── parseClaims(token)   │
    │                               │     ├── verifyWith(secretKey)│
    │                               │     ├── Kiểm tra signature   │
    │                               │     └── Kiểm tra expiration  │
    │                               │                              │
    │                       ┌───────┤                              │
    │              INVALID  │ VALID │                              │
    │                       │       │                              │
    │              Token    │  5.   │ jwtUtil.extractUserId()      │
    │              hết hạn/ │       │ → userId = "a1b2c3..."      │
    │              sai chữ  │       │                              │
    │              ký       │  6.   │ userRepository.findById()    │
    │                       │       │─────────────────────────────>│
    │              Không    │       │<─────────────────────────────│
    │              set       │       │                              │
    │              attribute │  7.   │ request.setAttribute(        │
    │                       │       │   CURRENT_USER, user)        │
    │                       │       │ request.setAttribute(        │
    │                       │       │   CURRENT_USER_ID, userId)   │
    │                       │       │                              │
    │                       │  8.   │ filterChain.doFilter()       │
    │                       │       │ → Chuyển tiếp request        │
    │                       │       │                              │
    │                               │  9. AuthInterceptor.preHandle()
    │                               │     ├── currentUser != null  │
    │                               │     ├── Path: /student/**    │
    │                               │     ├── Role: STUDENT ✓      │
    │                               │     └── return true          │
    │                               │                              │
    │                               │  10. Controller xử lý        │
    │                               │      request                 │
    │                               │                              │
    │  Response (HTML page)         │                              │
    │<──────────────────────────────│                              │
```

### 5.3 Luồng Token Hết hạn / Không hợp lệ

```
┌────────┐                    ┌────────────┐
│  User  │                    │   Server   │
└───┬────┘                    └─────┬──────┘
    │                               │
    │  1. GET /student/dashboard    │
    │  Cookie: token=eyJhbG...      │  (token hết hạn)
    │──────────────────────────────>│
    │                               │
    │                               │  2. JwtFilter.doFilterInternal()
    │                               │     token != null ✓
    │                               │     jwtUtil.validateToken(token)
    │                               │     → parseClaims() throw ExpiredJwtException
    │                               │     → validateToken() return false
    │                               │
    │                               │  3. KHÔNG set currentUser
    │                               │     currentUser = null
    │                               │
    │                               │  4. filterChain.doFilter()
    │                               │     → Chuyển tiếp request
    │                               │
    │                               │  5. AuthInterceptor.preHandle()
    │                               │     /student/dashboard ≠ public path
    │                               │     currentUser == null
    │                               │     → response.sendRedirect("/auth/login")
    │                               │     → return false
    │                               │
    │  302 Redirect → /auth/login   │
    │<──────────────────────────────│
    │                               │
    │  6. GET /auth/login           │
    │──────────────────────────────>│
    │                               │
    │  Hiển thị form đăng nhập      │
    │<──────────────────────────────│
```

### 5.4 Luồng Đăng xuất (Xóa JWT)

```
┌────────┐                    ┌────────────┐
│  User  │                    │   Server   │
└───┬────┘                    └─────┬──────┘
    │                               │
    │  1. GET /auth/logout          │
    │  Cookie: token=eyJhbG...      │
    │──────────────────────────────>│
    │                               │
    │                               │  2. Tạo cookie mới:
    │                               │     - Name: token
    │                               │     - Value: null
    │                               │     - HttpOnly: true
    │                               │     - Path: /
    │                               │     - MaxAge: 0  ← XÓA NGAY
    │                               │
    │                               │  3. request.getSession().invalidate()
    │                               │     → Xóa toàn bộ session data
    │                               │
    │  Set-Cookie: token=;          │
    │  Max-Age=0; Path=/            │
    │  Redirect → /auth/login       │
    │<──────────────────────────────│
```

### 5.5 Luồng WebSocket JWT Authentication

```
┌────────┐                    ┌────────────┐
│Browser │                    │   Server   │
└───┬────┘                    └─────┬──────┘
    │                               │
    │  1. new WebSocket(            │
    │     "ws://host/ws/meeting/1") │
    │  → Browser gửi HTTP Upgrade   │
    │    kèm theo Cookie: token=... │
    │──────────────────────────────>│
    │                               │
    │                               │  2. JwtFilter.doFilterInternal()
    │                               │     Đọc cookie → validate JWT
    │                               │     Set CURRENT_USER attribute
    │                               │
    │                               │  3. AuthHandshakeInterceptor
    │                               │     .beforeHandshake()
    │                               │     ├── Đọc User từ request attr
    │                               │     ├── user != null →
    │                               │     │   attributes.put("userId", user.getId())
    │                               │     │   attributes.put("userName", user.getFullName())
    │                               │     │   attributes.put("userRole", user.getRole())
    │                               │     │   return true → Handshake thành công
    │                               │     └── user == null →
    │                               │         return false → Handshake bị TỪ CHỐI
    │                               │
    │  WebSocket Connection         │
    │  Established (101 Switching)  │
    │<──────────────────────────────│
    │                               │
    │  4. Giao tiếp WebSocket       │
    │  (không cần gửi lại JWT)      │
    │◄─────────────────────────────►│
```

---

## 6. Chi tiết triển khai mã nguồn

### 6.1 JwtUtil — Tạo và Xác thực Token

```java
@Component
public class JwtUtil {

    private final SecretKey secretKey;       // HMAC-SHA key (từ config)
    private final long expirationMs;          // 86400000 ms = 24 giờ

    // Khởi tạo: chuyển secret string → SecretKey
    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(UTF_8));
        this.expirationMs = expirationMs;
    }

    // TẠO TOKEN
    public String generateToken(String userId) {
        return Jwts.builder()
                .subject(userId)                    // sub = userId
                .issuedAt(new Date())               // iat = now
                .expiration(new Date(now + expMs))  // exp = now + 24h
                .signWith(secretKey)                // Ký bằng HMAC-SHA256
                .compact();
    }

    // TRÍCH XUẤT USER ID
    public String extractUserId(String token) {
        return parseClaims(token).getSubject();
    }

    // KIỂM TRA HỢP LỆ
    public boolean validateToken(String token) {
        try {
            parseClaims(token);    // Nếu parse được → hợp lệ
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;          // Signature sai, hết hạn, format lỗi
        }
    }

    // PARSE CLAIMS (dùng chung)
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)             // Verify signature
                .build()
                .parseSignedClaims(token)          // Parse + validate exp
                .getPayload();
    }
}
```

### 6.2 JwtFilter — Đọc Token trên Mọi Request

```java
@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Value("${app.jwt.cookie-name}")
    private String cookieName;  // "token"

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) {

        // Bước 1: Đọc token từ cookie
        String token = extractTokenFromCookie(request);

        // Bước 2: Validate + Load user
        if (token != null && jwtUtil.validateToken(token)) {
            String userId = jwtUtil.extractUserId(token);
            Optional<User> userOpt = userRepository.findById(userId);

            // Bước 3: Set vào request attribute
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                request.setAttribute("currentUser", user);
                request.setAttribute("currentUserId", user.getId());
            }
        }

        // Bước 4: LUÔN cho request đi tiếp
        // (AuthInterceptor sẽ kiểm tra quyền sau)
        filterChain.doFilter(request, response);
    }

    // Bỏ qua filter cho static resources
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/css/") ||
               path.startsWith("/js/") ||
               path.startsWith("/images/") ||
               path.startsWith("/webjars/") ||
               path.startsWith("/favicon.ico");
    }
}
```

**Điểm quan trọng:**
- `JwtFilter` **KHÔNG BAO GIỜ chặn request** — nó chỉ set attribute
- Nếu token không hợp lệ → `currentUser` = `null` → `AuthInterceptor` sẽ xử lý
- `OncePerRequestFilter` đảm bảo filter chỉ chạy 1 lần mỗi request (không lặp vô hạn với forwards)

### 6.3 AuthInterceptor — Phân quyền theo Role

```java
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String path = request.getRequestURI();
        User currentUser = (User) request.getAttribute("currentUser");

        // Public paths — không cần đăng nhập
        if (isPublicPath(path)) return true;

        // Chưa đăng nhập → redirect login
        if (currentUser == null) {
            response.sendRedirect("/auth/login");
            return false;
        }

        // Kiểm tra role theo path
        Role role = currentUser.getRole();

        if (path.startsWith("/student/") && role != Role.STUDENT) {
            response.sendRedirect("/" + role.name().toLowerCase() + "/dashboard");
            return false;
        }
        if (path.startsWith("/lecturer/") && role != Role.LECTURER) {
            response.sendRedirect("/" + role.name().toLowerCase() + "/dashboard");
            return false;
        }
        if (path.startsWith("/admin/") && role != Role.ADMIN) {
            response.sendRedirect("/" + role.name().toLowerCase() + "/dashboard");
            return false;
        }
        if (path.startsWith("/meeting/") && role != Role.STUDENT && role != Role.LECTURER) {
            response.sendRedirect("/" + role.name().toLowerCase() + "/dashboard");
            return false;
        }

        return true; // Cho phép đi tiếp
    }

    private boolean isPublicPath(String path) {
        return path.equals("/") ||
               (path.startsWith("/auth/") && !path.startsWith("/auth/2fa-setup")) ||
               path.startsWith("/css/") || path.startsWith("/js/") ||
               path.startsWith("/images/") || path.startsWith("/webjars/") ||
               path.startsWith("/error") || path.equals("/favicon.ico") ||
               path.startsWith("/api/");
    }
}
```

---

## 7. Phân loại đường dẫn (Path Classification)

### 7.1 Public Paths (Không cần JWT)

| Path | Mô tả |
|------|--------|
| `/` | Trang chủ → redirect theo role |
| `/auth/login` | Form đăng nhập |
| `/auth/register` | Form đăng ký |
| `/auth/verify-2fa` | Xác thực 2FA (dùng session tạm) |
| `/auth/logout` | Đăng xuất |
| `/css/**`, `/js/**`, `/images/**` | Static resources |
| `/error` | Trang lỗi |
| `/api/**` | API endpoints (AJAX) |

### 7.2 Protected Paths (Cần JWT + Role phù hợp)

| Path Pattern | Role yêu cầu | Redirect nếu sai role |
|-------------|-------------|----------------------|
| `/student/**` | STUDENT | `/{role}/dashboard` |
| `/lecturer/**` | LECTURER | `/{role}/dashboard` |
| `/admin/**` | ADMIN | `/{role}/dashboard` |
| `/meeting/**` | STUDENT hoặc LECTURER | `/{role}/dashboard` |
| `/auth/2fa-setup/**` | Bất kỳ user đã đăng nhập | `/auth/login` |
| `/profile/**` | Bất kỳ user đã đăng nhập | `/auth/login` |

---

## 8. API Endpoints liên quan JWT

### 8.1 Token Generation Endpoints

| Method | Path | Trigger | JWT Action |
|--------|------|---------|------------|
| `POST` | `/auth/login` | Đăng nhập thành công (không 2FA) | Tạo JWT → Set Cookie |
| `POST` | `/auth/verify-2fa` | Xác thực 2FA thành công | Tạo JWT → Set Cookie |
| `GET` | `/auth/logout` | Đăng xuất | Xóa Cookie (MaxAge=0) + Invalidate Session |

### 8.2 Cookie Lifecycle

```
┌─────────────────────────────────────────────────────────────────┐
│                    COOKIE LIFECYCLE                              │
│                                                                  │
│  TẠO (Login / Verify 2FA):                                      │
│  Cookie cookie = new Cookie("token", jwtToken);                 │
│  cookie.setHttpOnly(true);                                       │
│  cookie.setPath("/");                                            │
│  cookie.setMaxAge(86400);  // 24 hours                         │
│  response.addCookie(cookie);                                     │
│                                                                  │
│  GỬI (Mỗi request):                                             │
│  Browser tự động gửi Cookie: token=eyJhbG...                    │
│  (vì Path=/ → khớp mọi path trên cùng domain)                  │
│                                                                  │
│  XÓA (Logout):                                                   │
│  Cookie cookie = new Cookie("token", null);                     │
│  cookie.setHttpOnly(true);                                       │
│  cookie.setPath("/");                                            │
│  cookie.setMaxAge(0);  // ← Xóa ngay lập tức                  │
│  response.addCookie(cookie);                                     │
│  request.getSession().invalidate();  // Xóa session             │
└─────────────────────────────────────────────────────────────────┘
```

---

## 9. Cấu trúc file

```
src/main/java/com/projectit210/
├── security/
│   ├── JwtUtil.java                 ← Tạo, validate, parse JWT (HMAC-SHA256)
│   ├── JwtFilter.java               ← OncePerRequestFilter: đọc cookie → set attribute
│   ├── PasswordEncoderConfig.java   ← @Bean BCryptPasswordEncoder
│   └── SecurityConfig.java          ← File trống (dự phòng)
├── config/
│   ├── AuthInterceptor.java         ← HandlerInterceptor: kiểm tra role theo path
│   └── WebConfig.java               ← Đăng ký AuthInterceptor
├── controller/
│   └── AuthController.java          ← Login/Logout/2FA (tạo + xóa JWT cookie)
├── constant/
│   └── AppConstant.java             ← CURRENT_USER, CURRENT_USER_ID keys
├── websocket/
│   └── AuthHandshakeInterceptor.java ← Xác thực JWT cho WebSocket handshake
└── util/
    └── SecurityUtil.java            ← Helper: lấy currentUser từ request

src/main/resources/
├── application.properties           ← JWT config: secret, expiration, cookie-name
```

---

## 10. Xử lý lỗi

| Tình huống | Xử lý | Kết quả |
|------------|-------|---------|
| Không có cookie `token` | `JwtFilter`: token = null → không set attribute | `currentUser` = null → `AuthInterceptor` redirect `/auth/login` |
| Token hết hạn (`exp` < now) | `JwtUtil.validateToken()`: `parseClaims()` throw `ExpiredJwtException` → return false | `currentUser` = null → redirect `/auth/login` |
| Token bị sửa đổi (signature sai) | `JwtUtil.validateToken()`: `parseClaims()` throw `SignatureException` → return false | `currentUser` = null → redirect `/auth/login` |
| Token format không hợp lệ | `JwtUtil.validateToken()`: `parseClaims()` throw `MalformedJwtException` → return false | `currentUser` = null → redirect `/auth/login` |
| User ID trong token không tồn tại trong DB | `JwtFilter`: `findById()` return empty → không set attribute | `currentUser` = null → redirect `/auth/login` |
| User bị vô hiệu hóa (`isActive = false`) | `JwtFilter` vẫn set attribute (user tồn tại trong DB) | Controller cần tự kiểm tra `isActive` |
| User có JWT nhưng sai role | `JwtFilter` set attribute → `AuthInterceptor` kiểm tra role | Redirect đến dashboard đúng role |
| Truy cập public path không có JWT | `JwtFilter` chạy nhưng không set → `AuthInterceptor` cho phép | Xử lý bình thường |
| WebSocket handshake không có JWT | `JwtFilter` không set → `AuthHandshakeInterceptor` return false | Handshake bị từ chối (HTTP 403) |

---

## 11. Bảo mật

### 11.1 Các biện pháp bảo mật đã áp dụng

| Biện pháp | Chi tiết |
|-----------|----------|
| **HttpOnly Cookie** | JavaScript không thể đọc token → chống XSS |
| **HMAC-SHA256 Signature** | Token không thể bị giả mạo → chống tampering |
| **Server-side Validation** | Mỗi request đều validate token + load user từ DB |
| **No Spring Security** | Kiểm soát hoàn toàn, không có default behavior bất ngờ |
| **Token chỉ chứa User ID** | Không expose role, username trong JWT payload |
| **Expiration 24h** | Token tự hết hạn, giảm rủi ro nếu bị đánh cắp |

### 11.2 Các giới hạn / Điểm cần cải thiện

| Giới hạn | Mô tả | Đề xuất |
|----------|-------|---------|
| **Không có Refresh Token** | User phải đăng nhập lại sau 24h | Thêm refresh token rotation |
| **Không có Token Blacklist** | Đăng xuất không vô hiệu token trên server (chỉ xóa cookie) | Thêm blacklist trong DB/Redis |
| **Secure flag tắt** | Cookie gửi qua HTTP (dev mode) | Bật `cookie.setSecure(true)` trong production |
| **SameSite không đặt** | Cookie có thể gửi trong cross-site request | Thêm `cookie.setAttribute("SameSite", "Lax")` |
| **Secret key hardcoded** | Secret nằm trong `application.properties` | Dùng environment variable hoặc vault |
| **User bị khóa vẫn có token hợp lệ** | `JwtFilter` không kiểm tra `isActive` | Thêm check `isActive` trong filter |
| **Không có rate limiting** | Brute force đăng nhập không bị giới hạn | Thêm rate limit cho `/auth/login` |

---

## 12. So sánh với Spring Security

| Tiêu chí | Dự án hiện tại | Spring Security |
|----------|----------------|-----------------|
| Cấu hình | Thủ công (Filter + Interceptor) | Auto-configuration |
| Filter chain | 1 filter (`JwtFilter`) | Nhiều filters liên tiếp |
| Phân quyền | `AuthInterceptor` theo path | `@PreAuthorize`, `SecurityContext` |
| User details | `request.setAttribute()` | `SecurityContextHolder` |
| Logout | Xóa cookie + invalidate session | `SecurityContextLogoutHandler` |
| CSRF | Không bảo vệ (dùng cookie) | CSRF token mặc định |
| Session | Stateless (JWT) + Session (2FA) | Có thể stateful hoặc stateless |
| Phức tạp | Đơn giản, dễ hiểu | Phức tạp, nhiều abstraction |
