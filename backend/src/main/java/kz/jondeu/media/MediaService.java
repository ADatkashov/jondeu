package kz.jondeu.media;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import kz.jondeu.common.ApiException;
import kz.jondeu.config.AppProperties;
import kz.jondeu.identity.AuthUser;
import kz.jondeu.notifications.Notifier;
import kz.jondeu.projects.ProjectAccess;
import kz.jondeu.projects.ProjectRole.Perm;
import kz.jondeu.sync.ProjectEvents;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {
    public static final Set<String> CATEGORIES = Set.of("BEFORE", "DURING", "AFTER", "STAGE", "INSPECTION", "DOCUMENT", "PLAN", "CHAT", "PORTFOLIO");
    private static final Map<String, String> EXT = Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp", "application/pdf", "pdf");
    private static final long URL_TTL_SECONDS = 3600;

    public record MediaView(UUID id, UUID projectId, UUID stageId, UUID uploaderId, String category, String filename, String contentType, long sizeBytes,
                            String caption, Instant createdAt, String url) {}

    private final MediaRepository repo;
    private final FileStorage storage;
    private final ProjectAccess access;
    private final ProjectEvents events;
    private final Notifier notifier;
    private final AppProperties props;
    private final byte[] hmacKey;

    public MediaService(MediaRepository repo, FileStorage storage, ProjectAccess access, ProjectEvents events, Notifier notifier, AppProperties props) {
        this.repo = repo; this.storage = storage; this.access = access; this.events = events; this.notifier = notifier; this.props = props;
        this.hmacKey = ("media:" + props.jwt().secret()).getBytes(StandardCharsets.UTF_8);
    }

    public MediaView view(Media m) {
        long exp = Instant.now().getEpochSecond() + URL_TTL_SECONDS;
        return new MediaView(m.getId(), m.getProjectId(), m.getStageId(), m.getUploaderId(), m.getCategory(), m.getFilename(), m.getContentType(), m.getSizeBytes(),
            m.getCaption(), m.getCreatedAt(), "/api/v1/media/" + m.getId() + "/file?exp=" + exp + "&sig=" + sign(m.getId(), exp));
    }

    @Transactional
    public MediaView upload(UUID projectId, MultipartFile file, String category, UUID stageId, String caption, AuthUser u) {
        access.require(projectId, u, Perm.UPLOAD);
        Media m = store(projectId, file, category == null ? "DURING" : category, stageId, caption, u.id());
        events.publish(projectId, "MEDIA_UPLOADED", "media", m.getId(), u.id(), ("DOCUMENT".equals(m.getCategory()) || "PLAN".equals(m.getCategory()) ? "Загружен документ: " : "Новый фотоотчёт: ") + m.getFilename());
        if (!"DOCUMENT".equals(m.getCategory())) notifier.project(projectId, u.id(), "MEDIA_UPLOADED", "Новый фотоотчёт", caption == null ? m.getFilename() : caption);
        return view(m);
    }

    /** Used by chat attachments (project may be null for direct conversations). */
    @Transactional
    public Media store(UUID projectId, MultipartFile file, String category, UUID stageId, String caption, UUID uploader) {
        if (!CATEGORIES.contains(category)) throw ApiException.bad("INVALID_CATEGORY", "Unknown category " + category);
        if (file == null || file.isEmpty()) throw ApiException.bad("EMPTY_FILE", "File is empty");
        if (file.getSize() > props.storage().maxBytes()) throw new ApiException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File is too large");
        byte[] head;
        try (var in = file.getInputStream()) { head = in.readNBytes(12); } catch (IOException e) { throw ApiException.bad("UNREADABLE_FILE", "Cannot read file"); }
        String type = sniff(head);
        if (type == null) throw new ApiException(org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", "Only JPEG, PNG, WebP and PDF are allowed");
        if (file.getContentType() != null && !file.getContentType().equalsIgnoreCase(type) && !file.getContentType().equals("application/octet-stream")) {
            throw new ApiException(org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE, "CONTENT_TYPE_MISMATCH", "File content does not match its declared type");
        }
        Media m = new Media();
        m.setProjectId(projectId); m.setStageId(stageId); m.setUploaderId(uploader); m.setCategory(category);
        m.setFilename(sanitize(file.getOriginalFilename(), EXT.get(type))); m.setContentType(type); m.setSizeBytes(file.getSize());
        m.setCaption(caption == null ? null : caption.substring(0, Math.min(caption.length(), 300)));
        m.setStorageKey((projectId == null ? "direct" : projectId.toString()) + "/" + m.getId() + "." + EXT.get(type));
        try (var in = file.getInputStream()) { storage.put(m.getStorageKey(), in, file.getSize(), type); } catch (IOException e) { throw new IllegalStateException("Storage failure", e); }
        return repo.save(m);
    }

    @Transactional(readOnly = true)
    public List<MediaView> list(UUID projectId, UUID stageId, String category, UUID uploader, Instant from, Instant to, AuthUser u) {
        access.require(projectId, u, Perm.VIEW);
        return repo.search(projectId, stageId, category, uploader, from == null ? Instant.EPOCH : from, to == null ? Instant.parse("2999-01-01T00:00:00Z") : to)
            .stream().map(this::view).toList();
    }

    @Transactional
    public void delete(UUID id, AuthUser u) {
        Media m = repo.findById(id).orElseThrow(() -> ApiException.notFound("Media"));
        if (m.getProjectId() == null) throw ApiException.forbidden("Cannot delete");
        var p = access.require(m.getProjectId(), u, Perm.VIEW);
        boolean allowed = m.getUploaderId().equals(u.id()) || p.getOwnerId().equals(u.id()) || u.isAdmin();
        if (!allowed) throw ApiException.forbidden("Only the uploader or the project owner can delete files");
        try { storage.delete(m.getStorageKey()); } catch (IOException ignored) { /* orphaned blobs are swept by ops, DB row removal is authoritative */ }
        repo.delete(m);
        events.publish(m.getProjectId(), "MEDIA_DELETED", "media", id, u.id(), "Удалён файл: " + m.getFilename());
    }

    public record Download(Media media, Resource resource) {}

    public Download download(UUID id, long exp, String sig) {
        if (exp < Instant.now().getEpochSecond()) throw ApiException.forbidden("Link expired");
        if (!MessageDigest.isEqual(sign(id, exp).getBytes(StandardCharsets.UTF_8), (sig == null ? "" : sig).getBytes(StandardCharsets.UTF_8))) throw ApiException.forbidden("Invalid signature");
        Media m = repo.findById(id).orElseThrow(() -> ApiException.notFound("Media"));
        try {
            Resource r = storage.get(m.getStorageKey());
            if (!r.exists()) throw ApiException.notFound("File");
            return new Download(m, r);
        } catch (IOException e) {
            throw ApiException.notFound("File");
        }
    }

    public Media find(UUID id) { return repo.findById(id).orElseThrow(() -> ApiException.notFound("Media")); }

    private String sign(UUID id, long exp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((id + ":" + exp).getBytes(StandardCharsets.UTF_8))).substring(0, 40);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Detects the real type from magic bytes — the client-declared Content-Type is not trusted. */
    static String sniff(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') return "image/png";
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') return "image/webp";
        if (h.length >= 5 && h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F' && h[4] == '-') return "application/pdf";
        return null;
    }

    private static String sanitize(String name, String ext) {
        String base = name == null ? "file" : name.replaceAll("[\\\\/\\r\\n\"]", "_").replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        base = base.length() > 120 ? base.substring(base.length() - 120) : base;
        return base.isBlank() ? "file." + ext : base;
    }
}
