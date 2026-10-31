package kz.jondeu.config;

import kz.jondeu.sync.WsAuthInterceptor;
import kz.jondeu.sync.WsHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final WsHandler handler;
    private final WsAuthInterceptor interceptor;
    private final AppProperties props;

    public WebSocketConfig(WsHandler handler, WsAuthInterceptor interceptor, AppProperties props) {
        this.handler = handler; this.interceptor = interceptor; this.props = props;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry r) {
        r.addHandler(handler, "/ws").addInterceptors(interceptor)
            .setAllowedOrigins(props.cors().allowedOrigins().toArray(String[]::new));
    }
}
