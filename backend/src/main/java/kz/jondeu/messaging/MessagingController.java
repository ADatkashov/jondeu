package kz.jondeu.messaging;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kz.jondeu.identity.CurrentUser;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Messages")
public class MessagingController {
    public record SendRequest(String body, UUID mediaId) {}
    private final MessagingService svc;

    public MessagingController(MessagingService svc) { this.svc = svc; }

    @GetMapping("/conversations")
    public List<MessagingService.ConversationView> mine() { return svc.mine(CurrentUser.require()); }

    @GetMapping("/projects/{projectId}/conversation")
    public MessagingService.ConversationView project(@PathVariable UUID projectId) { return svc.projectConversation(projectId, CurrentUser.require()); }

    @PostMapping("/conversations/direct/{userId}")
    public MessagingService.ConversationView direct(@PathVariable UUID userId) { return svc.direct(userId, CurrentUser.require()); }

    @GetMapping("/conversations/{id}/messages")
    public List<MessagingService.MessageView> history(@PathVariable UUID id, @RequestParam(required = false) Instant before, @RequestParam(defaultValue = "50") int limit) {
        return svc.history(id, before, limit, CurrentUser.require());
    }

    @PostMapping("/conversations/{id}/messages")
    public MessagingService.MessageView send(@PathVariable UUID id, @RequestBody SendRequest r) { return svc.send(id, r.body(), r.mediaId(), CurrentUser.require()); }

    @PostMapping(value = "/conversations/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MessagingService.Attachment attach(@PathVariable UUID id, @RequestPart("file") MultipartFile file) { return svc.attach(id, file, CurrentUser.require()); }

    @PostMapping("/conversations/{id}/read")
    public Map<String, Object> read(@PathVariable UUID id) { return svc.markRead(id, CurrentUser.require()); }
}
