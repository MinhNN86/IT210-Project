# Tài liệu Xác thực Hai lớp (2FA) - Smart Academic Platform

## 1. Tổng quan

Hệ thống xác thực hai lớp (Two-Factor Authentication - 2FA) sử dụng giao thức **TOTP** (Time-based One-Time Password) theo chuẩn **RFC 6238**, tương thích với ứng dụng **Google Authenticator**, **Microsoft Authenticator**, **Authy** và các ứng dụng TOTP khác.

### Công nghệ sử dụng

| Thư viện | Phiên bản | Mục đích |
|----------|-----------|----------|
| `com.warrenstrange:googleauth` | 1.5.0 | Tạo secret key, xác thực mã TOTP |
| `com.google.zxing:core` + `javase` | 3.5.3 | Tạo mã QR code từ `otpauth://` URL |

---

## 2. Cấu trúc Database

### Bảng `users` - Các cột mới cho 2FA

```sql
ALTER TABLE users ADD COLUMN two_factor_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN two_factor_secret VARCHAR(100) DEFAULT NULL;
```

| Cột | Kiểu | Mặc định | Mô tả |
|-----|------|----------|--------|
| `two_factor_enabled` | BOOLEAN | FALSE | Trạng thái kích hoạt 2FA |
| `two_factor_secret` | VARCHAR(100) | NULL | Khóa bí mật TOTP (Base32 encoded) |

**Lưu ý:** Khi 2FA bị vô hiệu hóa, cột `two_factor_secret` sẽ bị xóa (set NULL) để bảo mật.

---

## 3. Kiến trúc hệ thống

### 3.1 Sơ đồ lớp (Class Diagram)

```
┌─────────────────────────────────────────────────────────────────┐
│                        CONTROLLER LAYER                         │
│                                                                  │
│  AuthController                                                  │
│  ├── POST /auth/login              (Bước 1: Username + Password)│
│  ├── GET  /auth/verify-2fa         (Hiển thị trang nhập TOTP)   │
│  ├── POST /auth/verify-2fa         (Bước 2: Xác thực mã TOTP)   │
│  ├── GET  /auth/2fa-setup          (Trang quản lý 2FA)          │
│  ├── POST /auth/2fa-setup/generate (Tạo secret + QR code)       │
│  ├── POST /auth/2fa-setup/enable   (Kích hoạt 2FA)              │
│  └── POST /auth/2fa-setup/disable  (Vô hiệu hóa 2FA)            │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        SERVICE LAYER                             │
│                                                                  │
│  AuthService (Interface)                                         │
│  ├── login(request)           → Xác thực username/password      │
│  ├── verifyTotp(userId, code) → Xác thực mã TOTP                │
│  ├── generateTwoFactorSetup(userId) → Tạo secret + QR           │
│  ├── enableTwoFactor(userId, code)  → Kích hoạt 2FA             │
│  └── disableTwoFactor(userId, code) → Vô hiệu hóa 2FA           │
│                                                                  │
│  AuthServiceImpl (Implementation)                                │
│  ├── UserRepository     → Truy vấn dữ liệu user                 │
│  ├── PasswordEncoder    → Hash/verify mật khẩu (BCrypt)         │
│  └── TotpUtil           → TOTP operations                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        UTILITY LAYER                             │
│                                                                  │
│  TotpUtil                                                        │
│  ├── generateSecret()              → Tạo secret key (Base32)    │
│  ├── verifyCode(secret, code)      → Xác thực mã TOTP           │
│  ├── getOtpAuthUrl(username, secret) → Tạo otpauth:// URL       │
│  └── generateQrCodeBase64(url, w, h) → Tạo QR code Base64       │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 Security Layer

```
┌─────────────────────────────────────────────────────────────────┐
│                     REQUEST FLOW                                 │
│                                                                  │
│  HTTP Request                                                    │
│      │                                                           │
│      ▼                                                           │
│  JwtFilter (OncePerRequestFilter)                                │
│  ├── Đọc JWT từ cookie                                          │
│  ├── Validate token                                              │
│  └── Set User vào request attribute (CURRENT_USER)               │
│      │                                                           │
│      ▼                                                           │
│  AuthInterceptor (HandlerInterceptor)                            │
│  ├── Public paths: /auth/login, /auth/register, /auth/verify-2fa│
│  ├── Protected paths: /auth/2fa-setup/** (cần đăng nhập)        │
│  └── Role-based paths: /student/**, /lecturer/**, /admin/**      │
│      │                                                           │
│      ▼                                                           │
│  Controller                                                      │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. Luồng hoạt động chi tiết

### 4.1 Luồng Đăng nhập với 2FA

```
┌────────┐                    ┌────────────┐                ┌─────────────┐
│  User  │                    │   Server   │                │  Database   │
└───┬────┘                    └─────┬──────┘                └──────┬──────┘
    │                               │                              │
    │  1. POST /auth/login          │                              │
    │  {username, password}         │                              │
    │──────────────────────────────>│                              │
    │                               │  2. Tìm user by username     │
    │                               │─────────────────────────────>│
    │                               │<─────────────────────────────│
    │                               │                              │
    │                               │  3. Verify password (BCrypt) │
    │                               │  4. Check isActive           │
    │                               │                              │
    │                               │  5. Kiểm tra twoFactorEnabled│
    │                               │                              │
    │            ┌──────────────────┤                              │
    │            │ 2FA DISABLED     │  2FA ENABLED                 │
    │            │                  │                              │
    │            │  6a. Generate JWT│  6b. Lưu userId vào session  │
    │            │  Set cookie      │      session.2FA_USER_ID     │
    │            │                  │      session.2FA_USERNAME    │
    │            │                  │                              │
    │  Redirect to dashboard        │  Redirect to /auth/verify-2fa│
    │<─────────────────────────────┤                              │
    │            │                  │                              │
    │            │                  │  7. GET /auth/verify-2fa     │
    │            │                  │<───────┐                     │
    │            │                  │        │ Hiển thị form       │
    │            │                  │        │ nhập mã 6 số        │
    │            │                  │        │                     │
    │            │                  │  8. POST /auth/verify-2fa    │
    │            │                  │      {code: "123456"}        │
    │            │                  │<───────┐                     │
    │            │                  │        │                     │
    │            │                  │  9. Lấy userId từ session    │
    │            │                  │  10. Lấy user từ DB          │
    │            │                  │─────────────────────────────>│
    │            │                  │<─────────────────────────────│
    │            │                  │                              │
    │            │                  │  11. Verify TOTP code        │
    │            │                  │      using GoogleAuth        │
    │            │                  │                              │
    │            │         ┌───────┤                              │
    │            │  INVALID │ VALID │                              │
    │            │         │       │                              │
    │            │         │  12. Xóa session attributes          │
    │            │         │  13. Generate JWT token              │
    │            │         │  14. Set cookie                      │
    │            │         │  15. Redirect to dashboard           │
    │            │         │                                      │
    │  Redirect to dashboard       │                              │
    │<─────────────────────────────┤                              │
```

### 4.2 Luồng Thiết lập 2FA (từ Profile)

```
┌────────┐                    ┌────────────┐                ┌─────────────┐
│  User  │                    │   Server   │                │  Database   │
└───┬────┘                    └─────┬──────┘                └──────┬──────┘
    │                               │                              │
    │  1. GET /auth/2fa-setup       │                              │
    │  (cần JWT cookie hợp lệ)      │                              │
    │──────────────────────────────>│                              │
    │                               │                              │
    │                               │  2. Đọc currentUser từ       │
    │                               │     request attribute         │
    │                               │                              │
    │  Hiển thị trang 2FA setup     │                              │
    │<──────────────────────────────│                              │
    │                               │                              │
    │  3. POST /auth/2fa-setup/generate                             │
    │──────────────────────────────>│                              │
    │                               │                              │
    │                               │  4. Kiểm tra 2FA chưa bật    │
    │                               │                              │
    │                               │  5. Tạo secret key mới        │
    │                               │     (GoogleAuthenticator      │
    │                               │      .createCredentials())    │
    │                               │                              │
    │                               │  6. Lưu secret vào DB         │
    │                               │     (twoFactorEnabled=false)  │
    │                               │─────────────────────────────>│
    │                               │<─────────────────────────────│
    │                               │                              │
    │                               │  7. Tạo otpauth:// URL        │
    │                               │     Format:                   │
    │                               │     otpauth://totp/           │
    │                               │       ISSUER:USERNAME         │
    │                               │       ?secret=XXX             │
    │                               │       &issuer=ISSUER          │
    │                               │       &algorithm=SHA1         │
    │                               │       &digits=6               │
    │                               │       &period=30              │
    │                               │                              │
    │                               │  8. Encode URL thành QR code  │
    │                               │     (ZXing, 300x300px, PNG)   │
    │                               │                              │
    │  Hiển thị QR code + secret    │                              │
    │<──────────────────────────────│                              │
    │                               │                              │
    │  9. User quét QR bằng         │                              │
    │     Google Authenticator      │                              │
    │     → App hiển thị mã 6 số   │                              │
    │                               │                              │
    │  10. POST /auth/2fa-setup/enable                              │
    │      {code: "123456"}         │                              │
    │──────────────────────────────>│                              │
    │                               │                              │
    │                               │  11. Lấy secret từ DB         │
    │                               │─────────────────────────────>│
    │                               │<─────────────────────────────│
    │                               │                              │
    │                               │  12. Verify TOTP code         │
    │                               │      using secret             │
    │                               │                              │
    │                       ┌───────┤                              │
    │              INVALID  │ VALID │                              │
    │                       │       │                              │
    │              Thông báo │  13.  │ Set twoFactorEnabled=true   │
    │              lỗi       │       │─────────────────────────────>│
    │                       │       │<─────────────────────────────│
    │                       │       │                              │
    │                       │  14.  │ Thông báo thành công         │
    │                       │       │                              │
    │  Hiển thị kết quả     │       │                              │
    │<──────────────────────────────│                              │
```

### 4.3 Luồng Vô hiệu hóa 2FA

```
┌────────┐                    ┌────────────┐
│  User  │                    │   Server   │
└───┬────┘                    └─────┬──────┘
    │                               │
    │  1. POST /auth/2fa-setup/disable
    │     {code: "123456"}          │
    │──────────────────────────────>│
    │                               │
    │                               │  2. Kiểm tra 2FA đang bật
    │                               │
    │                               │  3. Verify TOTP code
    │                               │     với secret hiện tại
    │                               │
    │                       ┌───────┤
    │              INVALID  │ VALID │
    │                       │       │
    │              Thông báo │  4.  │ Set twoFactorEnabled=false
    │              lỗi       │      │ Set twoFactorSecret=null
    │                       │       │
    │  Hiển thị kết quả     │       │
    │<──────────────────────────────│
```

---

## 5. API Endpoints

### 5.1 Endpoints xác thực (Public - không cần đăng nhập)

| Method | Path | Mô tả |
|--------|------|--------|
| `GET` | `/auth/login` | Hiển thị form đăng nhập |
| `POST` | `/auth/login` | Xác thực username/password → redirect đến verify-2fa hoặc dashboard |
| `GET` | `/auth/verify-2fa` | Hiển thị form nhập mã TOTP (yêu cầu session `2FA_USER_ID`) |
| `POST` | `/auth/verify-2fa` | Xác thực mã TOTP → tạo JWT → redirect đến dashboard |

### 5.2 Endpoints quản lý 2FA (Protected - cần đăng nhập)

| Method | Path | Mô tả |
|--------|------|--------|
| `GET` | `/auth/2fa-setup` | Hiển thị trang quản lý 2FA |
| `POST` | `/auth/2fa-setup/generate` | Tạo secret key + QR code mới |
| `POST` | `/auth/2fa-setup/enable` | Xác nhận và kích hoạt 2FA |
| `POST` | `/auth/2fa-setup/disable` | Vô hiệu hóa 2FA |

---

## 6. Cấu hình bảo mật

### 6.1 AuthInterceptor - Phân loại đường dẫn

```java
// Public paths (không cần đăng nhập):
/auth/login
/auth/register
/auth/verify-2fa      ← Public (sử dụng session tạm)
/auth/logout

// Protected paths (cần đăng nhập + JWT):
/auth/2fa-setup       ← Yêu cầu JWT cookie hợp lệ
/auth/2fa-setup/generate
/auth/2fa-setup/enable
/auth/2fa-setup/disable
```

### 6.2 Session Management

| Session Attribute | Đặt tại | Xóa tại | Mục đích |
|-------------------|---------|---------|----------|
| `2FA_USER_ID` | `POST /auth/login` (khi 2FA enabled) | `POST /auth/verify-2fa` (thành công) | Lưu tạm user ID để xác thực TOTP |
| `2FA_USERNAME` | `POST /auth/login` (khi 2FA enabled) | `POST /auth/verify-2fa` (thành công) | Hiển thị username trên trang verify |

### 6.3 TOTP Parameters

| Parameter | Giá trị | Mô tả |
|-----------|---------|--------|
| Algorithm | SHA-1 | Thuật toán HMAC |
| Digits | 6 | Số chữ số của mã OTP |
| Period | 30 giây | Thời gian mã có hiệu lực |
| Issuer | Smart Academic Platform | Tên hiển thị trong Authenticator |

---

## 7. Cấu trúc file

```
src/main/java/com/projectit210/
├── controller/
│   └── AuthController.java          ← 7 endpoints cho login + 2FA
├── service/
│   ├── AuthService.java             ← Interface: 5 methods (login, register, verifyTotp, generateSetup, enable, disable)
│   └── impl/
│       └── AuthServiceImpl.java     ← Implementation: TOTP logic
├── dto/request/
│   └── TotpVerifyRequest.java       ← DTO: mã TOTP 6 số
├── entity/
│   └── User.java                    ← Entity: +2 fields (twoFactorEnabled, twoFactorSecret)
├── util/
│   └── TotpUtil.java                ← Utility: TOTP generation, verification, QR code
└── config/
    └── AuthInterceptor.java         ← Interceptor: phân loại public/protected paths

src/main/resources/templates/auth/
├── verify-2fa.html                  ← Trang nhập mã TOTP khi đăng nhập
└── 2fa-setup.html                   ← Trang quản lý 2FA (kích hoạt/vô hiệu hóa)
```

---

## 8. Xử lý lỗi

| Tình huống | Xử lý |
|------------|-------|
| Đăng nhập sai mật khẩu | Thông báo "Tên đăng nhập hoặc mật khẩu không đúng" |
| Nhập sai mã TOTP | Thông báo "Mã xác thực không đúng. Vui lòng thử lại." |
| Mã TOTP hết hạn (30s) | GoogleAuth cho phép window ±1 period → mã cũ 30s vẫn có thể dùng |
| Truy cập verify-2fa không có session | Redirect về `/auth/login` |
| Truy cập 2fa-setup không có JWT | Redirect về `/auth/login` |
| Kích hoạt 2FA khi đã bật | Thông báo "2FA đã được kích hoạt" |
| Vô hiệu hóa 2FA khi chưa bật | Thông báo "2FA chưa được kích hoạt" |
| Tạo QR mới khi đã bật 2FA | Thông báo "Vui lòng vô hiệu hóa trước khi thiết lập lại" |

---

## 9. Hướng dẫn sử dụng

### 9.1 Kích hoạt 2FA (User)

1. Đăng nhập → vào trang **Hồ sơ cá nhân** (`/profile`)
2. Click **"Thiết lập 2FA"** ở section "Bảo mật hai lớp"
3. Click **"Tạo mã QR"**
4. Mở ứng dụng **Google Authenticator** trên điện thoại
5. Chọn **"Thêm tài khoản"** → **"Quét mã QR"**
6. Quét mã QR hiển thị trên màn hình
7. Nhập mã 6 số từ ứng dụng → Click **"Kích hoạt 2FA"**
8. Thông báo "Kích hoạt xác thực hai lớp thành công!"

### 9.2 Đăng nhập với 2FA

1. Truy cập `/auth/login`
2. Nhập username + password → Click **"Đăng nhập"**
3. Hệ thống chuyển đến trang **"Xác thực hai lớp"**
4. Mở Google Authenticator → nhập mã 6 số
5. Click **"Xác nhận"** → Chuyển đến dashboard

### 9.3 Vô hiệu hóa 2FA

1. Vào trang **Hồ sơ cá nhân** → Click **"Quản lý 2FA"**
2. Nhập mã 6 số từ Google Authenticator
3. Click **"Vô hiệu hóa 2FA"**

---

## 10. Bảo mật

- **Secret key** được lưu trữ trong database dưới dạng Base32 encoded string
- **TOTP code** chỉ có hiệu lực trong **30 giây** (có window ±1 period)
- **QR code** chỉ hiển thị khi setup, không lưu trữ trên server
- **Session tạm** (`2FA_USER_ID`) chỉ tồn tại trong quá trình xác thực 2FA, bị xóa ngay sau khi hoàn tất
- **Secret bị xóa** (set NULL) khi 2FA bị vô hiệu hóa
- **Không sử dụng** Google Charts API để tạo QR code (tránh gửi secret qua mạng)
- QR code được generate **server-side** bằng ZXing, hiển thị dưới dạng Base64 image
