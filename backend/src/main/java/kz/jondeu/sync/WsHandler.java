package kz.jondeu.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import kz.jondeu.identity.AuthUser;
import kz.jondeu.projects.ProjectAccess;
import kz.jondeu.projects.ProjectRole.Perm;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/** Protocol: client -> {"action":"subscribe"|"unsubscribe"|"ping","projectId":"..."}; server -> JSON events. */
@Component
public class WsHandler extends TextWebSocketHandler {
    private final WsHub hub;
    private final ProjectAccess access;
    private final ProjectEventRepository events;
    private final ObjectMapper mapper;

    public WsHandler(WsHub hub, ProjectAccess access, ProjectEventRepository events, ObjectMapper mapper) {
        this.hub = hub; this.access = access; this.events = events; this.mapper = mapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        AuthUser u = (AuthUser) session.getAttributes().get(WsAuthInterceptor.USER_ATTR);
        if (u == null) {
            try { session.close(CloseStatus.POLICY_VIOLATION); } catch (java.io.IOException ignored) { }
            return;
        }
        WsHub.Conn c = hub.register(session, u);
        hub.send(c, Map.of("kind", "hello", "userId", u.id()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WsHub.Conn c = hub.get(session);
        if (c == null) return;
        JsonNode n = mapper.readTree(message.getPayload());
        String action = n.path("action").asText("");
        switch (action) {
            case "ping" -> hub.send(c, Map.of("kind", "pong"));
            case "subscribe" -> {
                UUID pid = parse(n.path("projectId").asText(null));
                if (pid == null || !access.can(pid, c.user(), Perm.VIEW)) {
                    hub.send(c, Map.of("kind", "error", "code", "FORBIDDEN", "projectId", String.valueOf(pid)));
                    return;
                }
                c.projects().add(pid);
                hub.send(c, Map.of("kind", "subscribed", "projectId", pid, "lastSeq", events.lastSeq(pid)));
            }
            case "unsubscribe" -> {
                UUID pid = parse(n.path("projectId").asText(null));
                if (pid != null) c.projects().remove(pid);
            }
            default -> hub.send(c, Map.of("kind", "error", "code", "UNKNOWN_ACTION"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { hub.remove(session); }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable e) { hub.remove(session); }

    private static UUID parse(String s) {
        try { return s == null ? null : UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }
}
