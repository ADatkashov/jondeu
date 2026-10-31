package kz.jondeu.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kz.jondeu.identity.AuthUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

/**
 * Registry of live WebSocket sessions. Every client (Flutter app, web dashboard) connects once to /ws,
 * is automatically bound to its own user channel and explicitly subscribes to projects it may view.
 */
@Component
public class WsHub {
    private static final Logger log = LoggerFactory.getLogger(WsHub.class);

    public static class Conn {
        final WebSocketSession session;
        final AuthUser user;
        final Set<UUID> projects = ConcurrentHashMap.newKeySet();
        Conn(WebSocketSession s, AuthUser u) { session = s; user = u; }
        public AuthUser user() { return user; }
        public Set<UUID> projects() { return projects; }
    }

    private final Map<String, Conn> conns = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;

    public WsHub(ObjectMapper mapper) { this.mapper = mapper; }

    public Conn register(WebSocketSession s, AuthUser u) {
        Conn c = new Conn(new ConcurrentWebSocketSessionDecorator(s, 5_000, 256 * 1024), u);
        conns.put(s.getId(), c);
        return c;
    }

    public Conn get(WebSocketSession s) { return conns.get(s.getId()); }

    public void remove(WebSocketSession s) { conns.remove(s.getId()); }

    public boolean isOnline(UUID userId) { return conns.values().stream().anyMatch(c -> c.user.id().equals(userId)); }

    public int connectionCount() { return conns.size(); }

    public void toProject(UUID projectId, Object payload) {
        String json = write(payload);
        conns.values().stream().filter(c -> c.projects.contains(projectId)).forEach(c -> send(c, json));
    }

    public void toUser(UUID userId, Object payload) {
        String json = write(payload);
        conns.values().stream().filter(c -> c.user.id().equals(userId)).forEach(c -> send(c, json));
    }

    public void toUsers(Iterable<UUID> userIds, Object payload) {
        String json = write(payload);
        Set<UUID> set = new java.util.HashSet<>();
        userIds.forEach(set::add);
        conns.values().stream().filter(c -> set.contains(c.user.id())).forEach(c -> send(c, json));
    }

    public void send(Conn c, Object payload) { send(c, payload instanceof String s ? s : write(payload)); }

    private void send(Conn c, String json) {
        try {
            if (c.session.isOpen()) c.session.sendMessage(new TextMessage(json));
        } catch (IOException | RuntimeException e) {
            log.debug("ws send failed, closing: {}", e.getMessage());
            try { c.session.close(CloseStatus.SERVER_ERROR); } catch (IOException ignored) { }
        }
    }

    private String write(Object o) {
        try { return mapper.writeValueAsString(o); } catch (IOException e) { throw new IllegalStateException(e); }
    }
}
