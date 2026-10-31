package kz.jondeu.identity;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import kz.jondeu.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Fixed-window per-IP limiter for authentication endpoints (single-node; swap for Redis when scaling out). */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private record Window(long minute, AtomicInteger count) {}
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;

    public RateLimitFilter(AppProperties props) { this.limit = props.rateLimit().authPerMinute(); }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) { return !req.getRequestURI().startsWith("/api/v1/auth/"); }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        long minute = System.currentTimeMillis() / 60_000;
        String ip = req.getRemoteAddr();
        Window w = windows.compute(ip, (k, old) -> old == null || old.minute() != minute ? new Window(minute, new AtomicInteger()) : old);
        if (w.count().incrementAndGet() > limit) {
            res.setStatus(429);
            res.setContentType("application/json");
            res.getWriter().write("{\"status\":429,\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests\"}");
            return;
        }
        if (windows.size() > 10_000) windows.entrySet().removeIf(e -> e.getValue().minute() != minute);
        chain.doFilter(req, res);
    }
}
