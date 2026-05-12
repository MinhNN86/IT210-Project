# Tài liệu chức năng Video Call - Smart Academic Platform

## 1. Tổng quan

Hệ thống Video Call cho phép **Giảng viên** và **Sinh viên** thực hiện buổi tư vấn trực tuyến 1-1 thông qua trình duyệt, sử dụng công nghệ **WebRTC** (Web Real-Time Communication) để truyền tải âm thanh và video theo thời gian thực (peer-to-peer), kết hợp **WebSocket** làm kênh signaling để thiết lập kết nối.

### Công nghệ sử dụng

| Công nghệ | Phiên bản / Chi tiết | Mục đích |
|-----------|----------------------|----------|
| `spring-boot-starter-websocket` | 4.0.6 | WebSocket signaling server |
| WebRTC API | Native Browser API | Peer-to-peer audio/video truyền tải |
| STUN Server | `stun:stun.l.google.com:19302` | NAT traversal, ICE candidate discovery |
| `RTCPeerConnection` | Native Browser API | Quản lý kết nối WebRTC |
| `getUserMedia` / `getDisplayMedia` | Native Browser API | Truy cập camera, mic, chia sẻ màn hình |

---

## 2. Cấu trúc Database

### Bảng `mentoring_sessions` - Cột liên quan Video Call

```sql
ALTER TABLE mentoring_sessions ADD COLUMN meeting_active BOOLEAN DEFAULT FALSE;
```

| Cột | Kiểu | Mặc định | Mô tả |
|-----|------|----------|--------|
| `meeting_active` | BOOLEAN | FALSE | Trạng thái phòng họp (true = giảng viên đã mở phòng) |

**Logic:**
- Khi **Giảng viên** truy cập phòng họp → `meeting_active = true`
- Khi **Giảng viên** đóng phòng → `meeting_active = false`
- **Sinh viên** chỉ được vào phòng khi `meeting_active = true` và `status = CONFIRMED`

---

## 3. Kiến trúc hệ thống

### 3.1 Sơ đồ tổng quan kiến trúc

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        FRONTEND (Browser)                               │
│                                                                          │
│  meeting-room.html                                                       │
│  ├── WebSocket Client          → Kết nối ws://host/ws/meeting/{id}      │
│  ├── RTCPeerConnection         → WebRTC P2P connection                  │
│  ├── getUserMedia()            → Camera + Microphone                    │
│  ├── getDisplayMedia()         → Screen capture (chỉ Lecturer)          │
│  └── UI Controls               → Mic, Camera, Screen share, Hang up    │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
            ┌────────────────┼──────────────────┐
            │ WebSocket (Signaling)             │ WebRTC (Media)
            │                                    │
            ▼                                    ▼
┌──────────────────────────┐     ┌──────────────────────────────────────┐
│   BACKEND (Spring Boot)  │     │        STUN Server (Google)          │
│                          │     │                                      │
│  WebSocketConfig         │     │  stun:stun.l.google.com:19302        │
│  ├── /ws/meeting/**      │     │  stun:stun1.l.google.com:19302      │
│  │                       │     │                                      │
│  AuthHandshakeInterceptor│     │  → ICE Candidate discovery          │
│  ├── Xác thực JWT cookie │     │  → NAT type detection               │
│  └── Inject user info    │     └──────────────────────────────────────┘
│                          │
│  MeetingSignalingHandler │     ┌──────────────────────────────────────┐
│  ├── Quản lý rooms       │     │     Peer-to-Peer (Direct)            │
│  ├── Relay signaling     │     │                                      │
│  └── Broadcast events    │     │  Audio Track ←→ Audio Track          │
│                          │     │  Video Track ←→ Video Track          │
│  MeetingController       │     │  Screen Track → (one direction)      │
│  ├── GET /meeting/{id}   │     └──────────────────────────────────────┘
│  ├── POST /meeting/{id}/close
│  └── GET /meeting/{id}/status
└──────────────────────────┘
```

### 3.2 Sơ đồ lớp (Class Diagram)

```
┌─────────────────────────────────────────────────────────────────┐
│                        CONFIG LAYER                              │
│                                                                  │
│  WebSocketConfig (implements WebSocketConfigurer)                │
│  ├── registerWebSocketHandlers()                                │
│  │   ├── Handler: MeetingSignalingHandler → /ws/meeting/**      │
│  │   ├── Interceptor: AuthHandshakeInterceptor                  │
│  │   └── AllowedOrigins: *                                      │
│  └── createWebSocketContainer()                                 │
│      ├── maxTextMessageBufferSize: 128 KB                       │
│      └── maxBinaryMessageBufferSize: 128 KB                     │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                      WEBSOCKET LAYER                             │
│                                                                  │
│  AuthHandshakeInterceptor (implements HandshakeInterceptor)      │
│  ├── beforeHandshake()                                          │
│  │   ├── Đọc User từ request attribute (CURRENT_USER)           │
│  │   ├── Inject userId, userName, userRole vào WS session       │
│  │   └── Return false → Từ chối kết nối (chưa đăng nhập)       │
│  └── afterHandshake()                                           │
│                                                                  │
│  MeetingSignalingHandler (extends TextWebSocketHandler)          │
│  ├── Data Structures:                                           │
│  │   ├── rooms: Map<roomId, Set<WebSocketSession>> (Concurrent) │
│  │   └── sessionUserNames: Map<sessionId, userName>             │
│  ├── afterConnectionEstablished() → Tham gia phòng              │
│  ├── handleTextMessage()          → Relay signaling messages    │
│  ├── afterConnectionClosed()      → Rời phòng                  │
│  └── handleTransportError()       → Xử lý lỗi                  │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                       CONTROLLER LAYER                           │
│                                                                  │
│  MeetingController                                               │
│  ├── GET  /meeting/{sessionId}        → Vào phòng họp           │
│  ├── POST /meeting/{sessionId}/close  → Đóng phòng (Lecturer)   │
│  └── GET  /meeting/{sessionId}/status → Kiểm tra trạng thái     │
└─────────────────────────────────────────────────────────────────┘
```

### 3.3 Security Layer

```
┌─────────────────────────────────────────────────────────────────┐
│                     REQUEST FLOW                                 │
│                                                                  │
│  HTTP Request (GET /meeting/{id})                                │
│      │                                                           │
│      ▼                                                           │
│  JwtFilter (OncePerRequestFilter)                                │
│  ├── Đọc JWT từ cookie                                          │
│  ├── Validate token                                              │
│  └── Set User vào request attribute (CURRENT_USER)               │
│      │                                                           │
│      ▼                                                           │
│  AuthInterceptor (HandlerInterceptor)                            │
│  ├── /meeting/** → Yêu cầu STUDENT hoặc LECTURER role           │
│  └── Các role khác → Redirect về dashboard tương ứng            │
│      │                                                           │
│      ▼                                                           │
│  MeetingController                                               │
│  ├── Kiểm tra session tồn tại                                    │
│  ├── Kiểm tra status == CONFIRMED                                │
│  ├── Kiểm tra user là participant của buổi tư vấn               │
│  └── Student: kiểm tra meetingActive == true                     │
│                                                                  │
│  ─────────────────────────────────────────                      │
│                                                                  │
│  WebSocket Request (ws://host/ws/meeting/{id})                   │
│      │                                                           │
│      ▼                                                           │
│  JwtFilter → Đọc JWT, set CURRENT_USER                          │
│      │                                                           │
│      ▼                                                           │
│  AuthHandshakeInterceptor                                        │
│  ├── Đọc User từ request attribute                               │
│  ├── Inject userId, userName, userRole vào WS session attrs     │
│  └── Return false → Handshake bị từ chối (403)                  │
│      │                                                           │
│      ▼                                                           │
│  MeetingSignalingHandler                                         │
└── Xử lý signaling messages                                       │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. Giao thức Signaling (WebSocket Messages)

### 4.1 Danh sách Message Types

| Type | Hướng | Mô tả |
|------|-------|--------|
| `user-joined` | Server → Clients trong room | Thông báo có user mới tham gia |
| `user-left` | Server → Clients trong room | Thông báo user đã rời phòng |
| `room-info` | Server → New user | Gửi thông tin phòng (số participants) |
| `offer` | Client → Server → Client | WebRTC SDP Offer (từ Lecturer) |
| `answer` | Client → Server → Client | WebRTC SDP Answer (từ Student) |
| `ice-candidate` | Client → Server → Client | ICE Candidate trao đổi |
| `screen-share-started` | Client → Server → Client | Thông báo bắt đầu chia sẻ màn hình |
| `screen-share-stopped` | Client → Server → Client | Thông báo dừng chia sẻ màn hình |
| `mute-changed` | Client → Server → Client | Thông báo thay đổi trạng thái mic |
| `video-changed` | Client → Server → Client | Thông báo thay đổi trạng thái camera |

### 4.2 Cấu trúc JSON Messages

```json
// user-joined (Server gửi)
{
  "type": "user-joined",
  "userId": "123",
  "userName": "Nguyễn Văn A"
}

// user-left (Server gửi)
{
  "type": "user-left",
  "userId": "123",
  "userName": "Nguyễn Văn A"
}

// room-info (Server gửi cho user mới)
{
  "type": "room-info",
  "participantCount": 2
}

// offer (WebRTC SDP)
{
  "type": "offer",
  "sdp": "v=0\r\no=- 123456 2 IN IP4...",
  "userName": "Nguyễn Văn A"
}

// answer (WebRTC SDP)
{
  "type": "answer",
  "sdp": "v=0\r\no=- 789012 2 IN IP4...",
  "userName": "Nguyễn Văn A"
}

// ice-candidate
{
  "type": "ice-candidate",
  "candidate": "candidate:1 1 UDP 2130706431 192.168.1.1 54321 typ host",
  "sdpMid": "0",
  "sdpMLineIndex": 0
}

// screen-share-started
{
  "type": "screen-share-started",
  "userName": "Nguyễn Văn A"
}

// screen-share-stopped
{
  "type": "screen-share-stopped",
  "userName": "Nguyễn Văn A"
}

// mute-changed
{
  "type": "mute-changed",
  "muted": true,
  "userName": "Nguyễn Văn A"
}

// video-changed
{
  "type": "video-changed",
  "videoOff": false,
  "userName": "Nguyễn Văn A"
}
```

---

## 5. Luồng hoạt động chi tiết

### 5.1 Luồng Giảng viên mở phòng họp

```
┌────────┐                    ┌────────────┐                ┌─────────────┐
│Lecturer│                    │   Server   │                │  Database   │
└───┬────┘                    └─────┬──────┘                └──────┬──────┘
    │                               │                              │
    │  1. GET /meeting/{sessionId}  │                              │
    │  (Cookie: JWT token)          │                              │
    │──────────────────────────────>│                              │
    │                               │                              │
    │                               │  2. JwtFilter: đọc JWT,      │
    │                               │     set CURRENT_USER          │
    │                               │                              │
    │                               │  3. AuthInterceptor:         │
    │                               │     kiểm tra LECTURER role   │
    │                               │                              │
    │                               │  4. Tìm session by ID        │
    │                               │─────────────────────────────>│
    │                               │<─────────────────────────────│
    │                               │                              │
    │                               │  5. Kiểm tra:                │
    │                               │     - Session tồn tại?       │
    │                               │     - Status == CONFIRMED?   │
    │                               │     - Lecturer là người phụ  │
    │                               │       trách buổi tư vấn?     │
    │                               │                              │
    │                               │  6. Set meetingActive = true │
    │                               │─────────────────────────────>│
    │                               │<─────────────────────────────│
    │                               │                              │
    │                               │  7. Truyền model data:       │
    │                               │     sessionId, studentName,  │
    │                               │     lecturerName, userName,  │
    │                               │     userRole, sessionDate,   │
    │                               │     startTime, endTime       │
    │                               │                              │
    │  Render meeting-room.html     │                              │
    │<──────────────────────────────│                              │
    │                               │                              │
    │  8. JavaScript: connectWebSocket()                           │
    │  ws://host/ws/meeting/{id}    │                              │
    │──────────────────────────────>│                              │
    │                               │                              │
    │                               │  9. AuthHandshakeInterceptor │
    │                               │     Đọc User từ JWT          │
    │                               │     Inject vào WS session    │
    │                               │                              │
    │                               │  10. afterConnectionEstablished()
    │                               │      Thêm vào rooms map      │
    │                               │      Broadcast user-joined   │
    │                               │      Gửi room-info           │
    │                               │                              │
    │  11. setupLocalMedia()        │                              │
    │  getUserMedia({video, audio}) │                              │
    │  → Hiển thị local video      │                              │
    │  → Bắt đầu timer             │                              │
```

### 5.2 Luồng Sinh viên tham gia phòng

```
┌────────┐                    ┌────────────┐                ┌─────────────┐
│Student │                    │   Server   │                │  Database   │
└───┬────┘                    └─────┬──────┘                └──────┬──────┘
    │                               │                              │
    │  1. GET /meeting/{sessionId}  │                              │
    │──────────────────────────────>│                              │
    │                               │                              │
    │                               │  2. JwtFilter + AuthInterceptor
    │                               │                              │
    │                               │  3. Tìm session by ID        │
    │                               │─────────────────────────────>│
    │                               │<─────────────────────────────│
    │                               │                              │
    │                               │  4. Kiểm tra:                │
    │                               │     - Session tồn tại?       │
    │                               │     - Status == CONFIRMED?   │
    │                               │     - Student là người đặt?  │
    │                               │     - meetingActive == true? │
    │                               │                              │
    │            ┌──────────────────┤                              │
    │            │ meetingActive    │ meetingActive                │
    │            │ = false          │ = true                       │
    │            │                  │                              │
    │            │ Redirect về      │ Render meeting-room.html     │
    │            │ /student/sessions│                              │
    │            │ + thông báo lỗi  │                              │
    │<───────────┤                  │                              │
    │            │                  │                              │
    │            │                  │  5. WebSocket connect        │
    │            │                  │  setupLocalMedia()           │
    │            │                  │                              │
    │            │                  │  6. Server broadcast         │
    │            │                  │     user-joined đến Lecturer │
```

### 5.3 Luồng WebRTC Peer Connection Setup

```
┌────────┐                    ┌────────────┐                    ┌────────┐
│Lecturer│                    │ WS Server  │                    │Student │
│(Caller)│                    │  (Relay)   │                    │(Callee)│
└───┬────┘                    └─────┬──────┘                    └───┬────┘
    │                               │                               │
    │  Nhận user-joined từ Student  │                               │
    │<──────────────────────────────│                               │
    │                               │                               │
    │  1. initiateConnection()      │                               │
    │     createPeerConnection()    │                               │
    │     addCameraTracks()         │                               │
    │     + Dummy track cho screen  │                               │
    │                               │                               │
    │  2. createOffer()             │                               │
    │     setLocalDescription(offer)│                               │
    │                               │                               │
    │  3. Send offer                │                               │
    │──────────────────────────────>│                               │
    │                               │  4. Relay offer               │
    │                               │──────────────────────────────>│
    │                               │                               │
    │                               │  5. handleOffer()              │
    │                               │     createPeerConnection()    │
    │                               │     addCameraTracks()         │
    │                               │     setRemoteDescription(offer)│
    │                               │     createAnswer()            │
    │                               │     setLocalDescription(answer)│
    │                               │                               │
    │                               │  6. Send answer               │
    │                               │<──────────────────────────────│
    │  7. Relay answer              │                               │
    │<──────────────────────────────│                               │
    │                               │                               │
    │  setRemoteDescription(answer) │                               │
    │                               │                               │
    │  ═══════════════════════════════════════════════════════════  │
    │  ICE Candidate Exchange (song song với SDP exchange)         │
    │  ═══════════════════════════════════════════════════════════  │
    │                               │                               │
    │  onicecandidate               │                               │
    │  8. Send ice-candidate        │                               │
    │──────────────────────────────>│  9. Relay ice-candidate      │
    │                               │──────────────────────────────>│
    │                               │                               │
    │                               │  onicecandidate               │
    │                               │  10. Send ice-candidate       │
    │  11. Relay ice-candidate      │<──────────────────────────────│
    │<──────────────────────────────│                               │
    │                               │                               │
    │  ═══════════════════════════════════════════════════════════  │
    │  P2P Connection Established - Media streams flowing          │
    │  ═══════════════════════════════════════════════════════════  │
    │                               │                               │
    │  ontrack → Hiển thị remote   │              ontrack → Hiển   │
    │  video/audio                 │              thị remote video │
```

### 5.4 Luồng Chia sẻ màn hình (Lecturer → Student)

```
┌────────┐                    ┌────────────┐                    ┌────────┐
│Lecturer│                    │ WS Server  │                    │Student │
└───┬────┘                    └─────┬──────┘                    └───┬────┘
    │                               │                               │
    │  1. toggleScreenShare()       │                               │
    │     getDisplayMedia({video})  │                               │
    │                               │                               │
    │  2. replaceTrack trên         │                               │
    │     screenTransceiver.sender  │                               │
    │     (dummy → screen track)    │                               │
    │     → Không cần renegotiation │                               │
    │                               │                               │
    │  3. Giảm bitrate camera       │                               │
    │     2.5 Mbps → 300 kbps       │                               │
    │                               │                               │
    │  4. Switch sang Sharing Layout│                               │
    │                               │                               │
    │  5. Send screen-share-started │                               │
    │──────────────────────────────>│  6. Relay                    │
    │                               │──────────────────────────────>│
    │                               │                               │
    │                               │  7. Nhận screen-share-started│
    │                               │     remoteIsSharing = true    │
    │                               │     Switch sang Sharing Layout│
    │                               │     Hiển thị màn hình chia sẻ │
    │                               │                               │
    │  ═══════════════════════════════════════════════════════════  │
    │  Dừng chia sẻ màn hình                                       │
    │  ═══════════════════════════════════════════════════════════  │
    │                               │                               │
    │  8. stopScreenShareLocal()    │                               │
    │     replaceTrack: screen →    │                               │
    │     dummy track               │                               │
    │     Khôi phục bitrate camera  │                               │
    │                               │                               │
    │  9. Send screen-share-stopped │                               │
    │──────────────────────────────>│  10. Relay                   │
    │                               │──────────────────────────────>│
    │                               │                               │
    │                               │  11. Switch về Normal Layout │
```

### 5.5 Luồng Đóng phòng / Rời phòng

```
┌──────────────┐              ┌────────────┐              ┌──────────────┐
│   Lecturer   │              │   Server   │              │   Student    │
└──────┬───────┘              └─────┬──────┘              └──────┬───────┘
       │                            │                            │
       │  1. closeRoom()            │                            │
       │     ws.close()             │                            │
       │     cleanupMedia()         │                            │
       │     POST /meeting/{id}/close│                           │
       │───────────────────────────>│                            │
       │                            │  2. Set meetingActive=false │
       │                            │                            │
       │                            │  3. afterConnectionClosed()│
       │                            │  Broadcast user-left       │
       │                            │───────────────────────────>│
       │                            │                            │
       │                            │  4. Student nhận user-left │
       │                            │     cleanup PC, hide remote│
       │                            │     stopTimer              │
       │                            │                            │
       │  Redirect to               │                            │
       │  /lecturer/confirmed-sessions                            │
       │<───────────────────────────│                            │
```

---

## 6. API Endpoints

### 6.1 HTTP Endpoints (Protected - cần JWT cookie)

| Method | Path | Role | Mô tả |
|--------|------|------|--------|
| `GET` | `/meeting/{sessionId}` | STUDENT, LECTURER | Vào phòng họp (kiểm tra quyền + trạng thái) |
| `POST` | `/meeting/{sessionId}/close` | LECTURER | Đóng phòng họp (set `meetingActive = false`) |
| `GET` | `/meeting/{sessionId}/status` | STUDENT, LECTURER, ADMIN | Kiểm tra trạng thái phòng họp |

### 6.2 WebSocket Endpoint

| Protocol | Path | Mô tả |
|----------|------|--------|
| `WS` / `WSS` | `/ws/meeting/{sessionId}` | WebSocket signaling endpoint |

**Lưu ý:** Room ID được trích xuất từ URI path (segment cuối cùng là số).

### 6.3 Chi tiết Response

#### `GET /meeting/{sessionId}` - Trả về view `meeting-room.html`

| Model Attribute | Kiểu | Mô tả |
|----------------|------|--------|
| `sessionId` | Long | ID của buổi tư vấn |
| `studentName` | String | Tên sinh viên |
| `lecturerName` | String | Tên giảng viên |
| `sessionDate` | String | Ngày buổi tư vấn |
| `startTime` | String | Giờ bắt đầu |
| `endTime` | String | Giờ kết thúc |
| `userName` | String | Tên user hiện tại |
| `userRole` | String | Role (STUDENT / LECTURER) |

#### `POST /meeting/{sessionId}/close` - Trả về JSON

```json
// Thành công
{ "success": true, "message": "Đã đóng phòng họp" }

// Lỗi quyền
{ "error": "Chỉ giảng viên mới có thể đóng phòng" }
```

#### `GET /meeting/{sessionId}/status` - Trả về JSON

```json
{
  "meetingActive": true,
  "sessionId": 123
}
```

---

## 7. Cấu hình WebRTC

### 7.1 ICE Server Configuration

```javascript
var iceServers = {
  iceServers: [
    { urls: 'stun:stun.l.google.com:19302' },
    { urls: 'stun:stun1.l.google.com:19302' }
  ]
};
```

| Parameter | Giá trị | Mô tả |
|-----------|---------|--------|
| STUN Server 1 | `stun:stun.l.google.com:19302` | Google STUN server công khai |
| STUN Server 2 | `stun:stun1.l.google.com:19302` | Google STUN server dự phòng |
| TURN Server | Không cấu hình | Không sử dụng TURN relay |

### 7.2 Media Constraints

| Media | Thông số | Mô tả |
|-------|----------|--------|
| Camera | 1280×720, 30fps | Độ phân giải HD |
| Audio | true | Bật microphone |
| Screen Share | 1920×1080, 30fps (max 60fps) | Độ phân giải Full HD |

### 7.3 Bitrate Configuration

| Track | Bitrate | Mô tả |
|-------|---------|--------|
| Camera (bình thường) | 2.5 Mbps | Chất lượng cao |
| Camera (khi share screen) | 300 kbps | Giảm để ưu tiên màn hình |
| Screen Share | 8 Mbps | Chất lượng rất cao, ưu tiên độ nét |
| Dummy Track | 8 Mbps | Bitrate cao cho kênh screen dự phòng |

### 7.4 Signaling Role Assignment

| Role | Hành động | Mô tả |
|------|-----------|--------|
| **LECTURER** | Tạo Offer (Caller) | Luôn là người khởi tạo WebRTC connection |
| **STUDENT** | Tạo Answer (Callee) | Chỉ phản hồi Offer từ Lecturer |

---

## 8. Cấu trúc file

```
src/main/java/com/projectit210/
├── config/
│   └── WebSocketConfig.java              ← Cấu hình WebSocket endpoint + buffer size
├── controller/
│   └── MeetingController.java            ← 3 endpoints: vào phòng, đóng phòng, trạng thái
├── websocket/
│   ├── AuthHandshakeInterceptor.java     ← Xác thực JWT trước WebSocket handshake
│   └── MeetingSignalingHandler.java      ← Xử lý signaling: rooms, relay, broadcast
├── entity/
│   └── MentoringSession.java             ← Entity: +1 field (meetingActive)
├── constant/
│   └── AppConstant.java                  ← Hằng số: CURRENT_USER, CURRENT_USER_ID
└── config/
    └── AuthInterceptor.java              ← Interceptor: kiểm tra role cho /meeting/**

src/main/resources/templates/
└── meeting-room.html                     ← Frontend: WebRTC + WebSocket + UI controls
```

---

## 9. Xử lý lỗi

| Tình huống | Xử lý |
|------------|-------|
| Buổi tư vấn không tồn tại | Ném `ResourceNotFoundException`, hiển thị trang lỗi |
| Buổi tư vấn chưa xác nhận (status ≠ CONFIRMED) | Redirect + thông báo "Buổi tư vấn chưa được xác nhận" |
| Sinh viên không phải người đặt lịch | Redirect + thông báo "Bạn không có quyền tham gia" |
| Sinh viên vào khi giảng viên chưa mở phòng | Redirect + thông báo "Giảng viên chưa mở phòng họp" |
| Giảng viên không phải người phụ trách buổi tư vấn | Redirect + thông báo "Bạn không có quyền tham gia" |
| WebSocket kết nối thất bại (không có JWT) | Handshake bị từ chối, connection đóng |
| WebSocket bị ngắt | Tự động reconnect sau 3 giây |
| getUserMedia thất bại (không có camera/mic) | Hiển thị thông báo, vẫn cho phép tham gia |
| WebRTC connection disconnected/failed | Ẩn remote video, reset state |
| getDisplayMedia thất bại | Log lỗi, không crash |
| Đóng phòng khi không phải giảng viên | Return 403 "Chỉ giảng viên mới có thể đóng phòng" |

---

## 10. Tối ưu hóa kỹ thuật

### 10.1 Dummy Track Pre-allocation (Tránh Renegotiation)

```
Khi Lecturer tạo PeerConnection:
  1. Thêm camera track (audio + video)
  2. Thêm dummy track (canvas 10×10 đen, 30fps) cho screen share

Khi Lecturer share screen:
  → replaceTrack(dummy → screen track)
  → Không cần SDP renegotiation
  → Không gây disconnect

Khi Lecturer dừng share screen:
  → replaceTrack(screen track → dummy)
  → Không cần SDP renegotiation
```

**Lợi ích:**
- Tránh vòng lặp disconnect/reconnect khi renegotiation thất bại
- `replaceTrack()` không thay đổi SDP → ổn định hơn
- Dummy track giữ kênh screen luôn sẵn sàng

### 10.2 Bandwidth Adaptation

```
Bình thường:
  Camera: 2.5 Mbps

Khi Share Screen:
  Camera: 300 kbps (giảm 92%)
  Screen: 8 Mbps (ưu tiên)
  degradationPreference: maintain-resolution

Khi Dừng Share Screen:
  Camera: 2.5 Mbps (khôi phục)
  scaleResolutionDownBy: 1 (full resolution)
```

### 10.3 Layout Modes

| Mode | Layout | Điều kiện |
|------|--------|-----------|
| **Normal** | 2 video cạnh nhau (50/50) | Không có screen share |
| **Sharing** | Screen lớn + sidebar 2 PiP | Lecturer đang share screen |

### 10.4 WebSocket Auto-Reconnect

```javascript
ws.onclose = function() {
  showToast('Kết nối bị ngắt. Thử lại...');
  // Không phá PeerConnection khi WS ngắt tạm thời
  setTimeout(connectWebSocket, 3000);
};
```

- WebSocket reconnect tự động sau 3 giây
- PeerConnection không bị đóng khi WS ngắt tạm thời
- Chỉ gọi `setupLocalMedia()` lần đầu kết nối (`wsConnected` flag)

---

## 11. Hướng dẫn sử dụng

### 11.1 Giảng viên - Mở phòng và bắt đầu Video Call

1. Đăng nhập → vào **"Buổi đã xác nhận"** (`/lecturer/confirmed-sessions`)
2. Click **"Vào phòng họp"** ở buổi tư vấn muốn thực hiện
3. Trình duyệt yêu cầu quyền camera/microphone → Click **"Cho phép"**
4. Phòng họp mở, chờ sinh viên tham gia
5. Khi sinh viên vào → Video call tự động kết nối

### 11.2 Sinh viên - Tham gia Video Call

1. Đăng nhập → vào **"Buổi của tôi"** (`/student/sessions`)
2. Click **"Tham gia phòng"** ở buổi tư vấn đã xác nhận
   - Nếu giảng viên chưa vào → Hiển thị thông báo chờ đợi
3. Khi giảng viên đã mở phòng → Trình duyệt yêu cầu quyền camera/mic
4. Video call tự động kết nối

### 11.3 Điều khiển trong phòng họp

| Nút | Icon | Chức năng | Phân quyền |
|-----|------|-----------|------------|
| Mic | 🎤 / 🎤❌ | Bật/tắt microphone | Tất cả |
| Camera | 📹 / 📹❌ | Bật/tắt camera | Tất cả |
| Share Screen | 🖥️ | Chia sẻ/dừng chia sẻ màn hình | Chỉ Lecturer |
| Đóng phòng | 🚪 "Đóng phòng" | Đóng phòng họp, set `meetingActive=false` | Chỉ Lecturer |
| Rời phòng | 📞❌ "Rời phòng" | Rời phòng họp | Chỉ Student |

### 11.4 Giảng viên - Chia sẻ màn hình

1. Click nút **"Chia sẻ màn hình"** (🖥️)
2. Trình duyệt hiển thị dialog chọn nguồn chia sẻ
3. Chọn màn hình/cửa sổ/tab → Click **"Share"**
4. Layout chuyển sang chế độ Sharing (màn hình lớn + sidebar)
5. Click lại nút để dừng chia sẻ

### 11.5 Giảng viên - Đóng phòng họp

1. Click nút **"Đóng phòng"** (🚪)
2. Hệ thống gọi `POST /meeting/{id}/close` → `meetingActive = false`
3. Sinh viên nhận event `user-left` → cleanup
4. Giảng viên được redirect về trang buổi đã xác nhận

---

## 12. Giới hạn và lưu ý

- **Không có TURN server:** WebRTC chỉ hoạt động trong môi trường NAT cho phép direct connection. Nếu cả hai phía đều ở sau symmetric NAT, kết nối có thể thất bại.
- **Room không persistent:** Danh sách rooms lưu trong `ConcurrentHashMap` trong memory. Restart server sẽ mất tất cả rooms.
- **Tối đa 2 participants:** Thiết kế cho 1-1 call. Không hỗ trợ multi-party.
- **Screen share chỉ cho Lecturer:** Sinh viên không có nút chia sẻ màn hình (kiểm soát cả server-side lẫn client-side).
- **Browser support:** Yêu cầu trình duyệt hỗ trợ WebRTC (Chrome, Firefox, Edge, Safari).
- **HTTPS requirement:** `getUserMedia()` và `getDisplayMedia()` yêu cầu HTTPS trong production (trừ localhost).
