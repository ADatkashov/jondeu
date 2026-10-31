package kz.jondeu.media;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kz.jondeu.identity.CurrentUser;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Media")
public class MediaController {
    private final MediaService svc;

    public MediaController(MediaService svc) { this.svc = svc; }

    @PostMapping(value = "/projects/{projectId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MediaService.MediaView upload(@PathVariable UUID projectId, @RequestPart("file") MultipartFile file, @RequestParam(defaultValue = "DURING") String category,
                                         @RequestParam(required = false) UUID stageId, @RequestParam(required = false) String caption) {
        return svc.upload(projectId, file, category, stageId, caption, CurrentUser.require());
    }

    @GetMapping("/projects/{projectId}/media")
    public List<MediaService.MediaView> list(@PathVariable UUID projectId, @RequestParam(required = false) UUID stageId, @RequestParam(required = false) String category,
                                             @RequestParam(required = false) UUID uploaderId, @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
        return svc.list(projectId, stageId, category, uploaderId, from, to, CurrentUser.require());
    }

    @DeleteMapping("/media/{id}")
    public Map<String, Boolean> delete(@PathVariable UUID id) { svc.delete(id, CurrentUser.require()); return Map.of("ok", true); }

    /** Publicly reachable but protected by an expiring HMAC signature (usable directly in <img> / Image.network). */
    @GetMapping("/media/{id}/file")
    public ResponseEntity<org.springframework.core.io.Resource> file(@PathVariable UUID id, @RequestParam long exp, @RequestParam String sig) {
        var d = svc.download(id, exp, sig);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.media().getContentType()))
            .cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(30)).cachePrivate())
            .header("Content-Disposition", "inline; filename=\"" + d.media().getFilename().replace("\"", "") + "\"")
            .header("X-Content-Type-Options", "nosniff")
            .body(d.resource());
    }
}
