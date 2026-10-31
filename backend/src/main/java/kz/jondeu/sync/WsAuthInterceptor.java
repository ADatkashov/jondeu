package kz.jondeu.sync;

import java.util.Map;
import kz.jondeu.identity.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/** Authenticates the WebSocket handshake with the short-lived access JWT passed as ?token=. */
@Component
public class WsAuthInterceptor implements HandshakeInterceptor {
    public static final String USER_ATTR = "authUser";
    private final JwtService jwt;

    public WsAuthInterceptor(JwtService jwt) { this.jwt = jwt; }

    @Override
    public boolean beforeHandshake(ServerHttpRequest req, ServerHttpResponse res, WebSocketHandler h, Map<String, Object> attrs) {
        if (req instanceof ServletServerHttpRequest s) {
            String token = s.getServletRequest().getParameter("token");
            if (token != null) {
                var user = jwt.parse(token);
                if (user.isPresent()) {
                    attrs.put(USER_ATTR, user.get());
                    return true;
                }
            }
        }
        res.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest req, ServerHttpResponse res, WebSocketHandler h, Exception ex) { }
}
