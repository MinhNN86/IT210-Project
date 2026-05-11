package com.projectit210.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class MeetingSignalingHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MeetingSignalingHandler.class);

    private static final Map<String, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();
    private static final Map<String, String> sessionUserNames = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String roomId = extractRoomId(session);
        String userId = getAttr(session, "userId");
        String userName = getAttr(session, "userName");

        if (roomId == null || userId == null) {
            closeSession(session);
            return;
        }

        rooms.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(session);
        sessionUserNames.put(session.getId(), userName);

        log.info("User {} ({}) joined room {}", userId, userName, roomId);

        String msg = buildJson("user-joined", "\"userId\":\"" + esc(userId) + "\",\"userName\":\"" + esc(userName) + "\"");
        broadcast(roomId, session, msg);

        Set<WebSocketSession> roomSessions = rooms.get(roomId);
        for (WebSocketSession s : roomSessions) {
            if (s.isOpen() && !s.getId().equals(session.getId())) {
                String existingUserId = getAttr(s, "userId");
                String existingUserName = sessionUserNames.get(s.getId());
                String existingMsg = buildJson("user-joined", "\"userId\":\"" + esc(existingUserId) + "\",\"userName\":\"" + esc(existingUserName) + "\"");
                sendMessage(session, existingMsg);
            }
        }

        int count = roomSessions.size();
        String welcome = buildJson("room-info", "\"participantCount\":" + count);
        sendMessage(session, welcome);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            String type = extractJsonString(payload, "type");
            String roomId = extractRoomId(session);

            if (roomId == null || type == null) return;

            switch (type) {
                case "offer":
                case "answer":
                case "ice-candidate":
                case "screen-share-started":
                case "screen-share-stopped":
                case "mute-changed":
                case "video-changed":
                    relayToPeers(roomId, session, payload);
                    break;
                default:
                    log.debug("Unknown message type: {}", type);
            }
        } catch (Exception e) {
            log.error("Error handling message: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomId = extractRoomId(session);
        String userId = getAttr(session, "userId");
        String userName = sessionUserNames.remove(session.getId());

        if (roomId != null) {
            Set<WebSocketSession> roomSessions = rooms.get(roomId);
            if (roomSessions != null) {
                roomSessions.remove(session);
                if (roomSessions.isEmpty()) {
                    rooms.remove(roomId);
                }
            }

            String msg = buildJson("user-left", "\"userId\":\"" + esc(userId) + "\",\"userName\":\"" + esc(userName) + "\"");
            broadcast(roomId, session, msg);

            log.info("User {} ({}) left room {}", userId, userName, roomId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Transport error for session {}: {}", session.getId(), exception.getMessage());
    }

    private void relayToPeers(String roomId, WebSocketSession sender, String payload) {
        Set<WebSocketSession> roomSessions = rooms.get(roomId);
        if (roomSessions == null) return;

        for (WebSocketSession s : roomSessions) {
            if (s.isOpen() && !s.getId().equals(sender.getId())) {
                sendMessage(s, payload);
            }
        }
    }

    private void broadcast(String roomId, WebSocketSession exclude, String payload) {
        Set<WebSocketSession> roomSessions = rooms.get(roomId);
        if (roomSessions == null) return;

        for (WebSocketSession s : roomSessions) {
            if (s.isOpen() && !s.getId().equals(exclude.getId())) {
                sendMessage(s, payload);
            }
        }
    }

    private void sendMessage(WebSocketSession session, String payload) {
        try {
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(payload));
                }
            }
        } catch (IOException e) {
            log.error("Failed to send message: {}", e.getMessage());
        }
    }

    private void closeSession(WebSocketSession session) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException ignored) {
        }
    }

    private String extractRoomId(WebSocketSession session) {
        String path = session.getUri() != null ? session.getUri().getPath() : "";
        String[] parts = path.split("/");
        if (parts.length >= 2) {
            String last = parts[parts.length - 1];
            if (last.matches("\\d+")) {
                return last;
            }
        }
        return null;
    }

    private String getAttr(WebSocketSession session, String key) {
        Object val = session.getAttributes().get(key);
        return val != null ? val.toString() : null;
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String buildJson(String type, String extra) {
        if (extra != null && !extra.isEmpty()) {
            return "{\"type\":\"" + type + "\"," + extra + "}";
        }
        return "{\"type\":\"" + type + "\"}";
    }

    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return null;
        return json.substring(start, end);
    }
}
