package kz.jondeu.notifications;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import java.util.UUID;
import kz.jondeu.common.ApiException;
import kz.jondeu.identity.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
public class NotificationController {
    private final NotificationRepository repo;

    public NotificationController(NotificationRepository repo) { this.repo = repo; }

    @GetMapping
    public Page<Notification> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
        return repo.findByUserIdOrderByCreatedAtDesc(CurrentUser.require().id(), PageRequest.of(page, Math.min(size, 100)));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unread() { return Map.of("count", repo.countByUserIdAndReadFalse(CurrentUser.require().id())); }

    @PostMapping("/{id}/read")
    @Transactional
    public Map<String, Boolean> read(@PathVariable UUID id) {
        Notification n = repo.findById(id).filter(x -> x.getUserId().equals(CurrentUser.require().id())).orElseThrow(() -> ApiException.notFound("Notification"));
        n.setRead(true);
        return Map.of("ok", true);
    }

    @PostMapping("/read-all")
    @Transactional
    public Map<String, Integer> readAll() { return Map.of("updated", repo.markAllRead(CurrentUser.require().id())); }
}
