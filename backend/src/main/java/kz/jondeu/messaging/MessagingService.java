package kz.jondeu.messaging;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import kz.jondeu.common.ApiException;
import kz.jondeu.identity.AuthUser;
import kz.jondeu.identity.User;
import kz.jondeu.identity.UserRepository;
import kz.jondeu.media.Media;
import kz.jondeu.media.MediaService;
import kz.jondeu.messaging.MessagingRepositories.*;
import kz.jondeu.notifications.Notifier;
import kz.jondeu.projects.*;
import kz.jondeu.projects.ProjectRole.Perm;
import kz.jondeu.sync.ProjectEvents;
import kz.jondeu.sync.WsHub;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MessagingService {
    public record Attachment(UUID id, String filename, String contentType, long sizeBytes, String url) {}
    public record MessageView(UUID id, UUID conversationId, UUID senderId, String senderName, String body, Attachment attachment, Instant createdAt, String status) {}
    public record ConversationView(UUID id, UUID projectId, String title, List<UUID> memberIds, MessageView lastMessage, long unread) {}

    private final ConversationRepository conversations;
    private final MemberRepository members;
    private final MessageRepository messages;
    private final UserRepository users;
    private final ProjectAccess access;
    private final ProjectRepository projects;
    private final ProjectMemberRepository projectMembers;
    private final MediaService media;
    private final WsHub hub;
    private final Notifier notifier;

    public MessagingService(ConversationRepository conversations, MemberRepository members, MessageRepository messages, UserRepository users, ProjectAccess access,
                            ProjectRepository projects, ProjectMemberRepository projectMembers, MediaService media, WsHub hub, Notifier notifier) {
        this.conversations = conversations; this.members = members; this.messages = messages; this.users = users; this.access = access;
        this.projects = projects; this.projectMembers = projectMembers; this.media = media; this.hub = hub; this.notifier = notifier;
    }

    /** Project conversation: created on demand; membership is re-synchronised with project membership on every access. */
    @Transactional
    public ConversationView projectConversation(UUID projectId, AuthUser u) {
        Project p = access.require(projectId, u, Perm.CHAT);
        Conversation c = conversations.findFirstByProjectId(projectId).orElseGet(() -> {
            Conversation n = new Conversation();
            n.setProjectId(projectId); n.setTitle(p.getName());
            return conversations.save(n);
        });
        Set<UUID> want = new HashSet<>();
        want.add(p.getOwnerId());
        projectMembers.findByProjectId(projectId).forEach(m -> want.add(m.getUserId()));
        Set<UUID> have = members.findByConversationId(c.getId()).stream().map(ConversationMember::getUserId).collect(Collectors.toSet());
        want.stream().filter(id -> !have.contains(id)).forEach(id -> members.save(new ConversationMember(c.getId(), id)));
        return view(c, u.id());
    }

    @Transactional
    public ConversationView direct(UUID otherUserId, AuthUser u) {
        if (otherUserId.equals(u.id())) throw ApiException.bad("INVALID_TARGET", "Cannot chat with yourself");
        users.findById(otherUserId).filter(User::isEnabled).orElseThrow(() -> ApiException.notFound("User"));
        Conversation c = conversations.findDirect(u.id(), otherUserId).stream().findFirst().orElseGet(() -> {
            Conversation n = new Conversation();
            conversations.save(n);
            members.save(new ConversationMember(n.getId(), u.id()));
            members.save(new ConversationMember(n.getId(), otherUserId));
            return n;
        });
        return view(c, u.id());
    }

    @Transactional(readOnly = true)
    public List<ConversationView> mine(AuthUser u) {
        return members.findByUserId(u.id()).stream().map(m -> conversations.findById(m.getConversationId()).orElse(null)).filter(Objects::nonNull)
            .map(c -> view(c, u.id()))
            .sorted(Comparator.comparing((ConversationView v) -> v.lastMessage() == null ? Instant.EPOCH : v.lastMessage().createdAt()).reversed()).toList();
    }

    @Transactional(readOnly = true)
    public List<MessageView> history(UUID conversationId, Instant before, int limit, AuthUser u) {
        Conversation c = requireMember(conversationId, u);
        var list = messages.findByConversationIdAndCreatedAtLessThanOrderByCreatedAtDesc(conversationId, before == null ? Instant.parse("2999-01-01T00:00:00Z") : before, PageRequest.of(0, Math.min(Math.max(limit, 1), 100)));
        return render(c, list);
    }

    @Transactional
    public MessageView send(UUID conversationId, String body, UUID mediaId, AuthUser u) {
        Conversation c = requireMember(conversationId, u);
        if ((body == null || body.isBlank()) && mediaId == null) throw ApiException.bad("VALIDATION_FAILED", "Message is empty");
        if (body != null && body.length() > 5000) throw ApiException.bad("VALIDATION_FAILED", "Message is too long");
        if (mediaId != null) {
            Media m = media.find(mediaId);
            if (!m.getUploaderId().equals(u.id()) || !"CHAT".equals(m.getCategory())) throw ApiException.forbidden("Attachment does not belong to you");
        }
        Message m = new Message();
        m.setConversationId(conversationId); m.setSenderId(u.id()); m.setBody(body == null ? null : body.trim()); m.setMediaId(mediaId);
        messages.save(m);
        List<UUID> recipients = members.findByConversationId(conversationId).stream().map(ConversationMember::getUserId).filter(id -> !id.equals(u.id())).toList();
        // the sender has implicitly read everything up to now
        members.findByConversationIdAndUserId(conversationId, u.id()).ifPresent(cm -> { cm.setLastReadAt(m.getCreatedAt()); members.save(cm); });
        MessageView v = render(c, List.of(m)).get(0);
        Map<String, Object> push = Map.of("kind", "chat.message", "conversationId", conversationId, "projectId", c.getProjectId() == null ? "" : c.getProjectId().toString(), "message", v);
        ProjectEvents.afterCommit(() -> hub.toUsers(recipients, push));
        // offline recipients get an in-app notification (and external push via Notifier channels)
        recipients.stream().filter(id -> !hub.isOnline(id)).forEach(id -> notifier.user(id, c.getProjectId(), "NEW_MESSAGE", u.fullName(), m.getBody() == null ? "📎" : m.getBody().substring(0, Math.min(80, m.getBody().length()))));
        return v;
    }

    @Transactional
    public Attachment attach(UUID conversationId, MultipartFile file, AuthUser u) {
        Conversation c = requireMember(conversationId, u);
        Media m = media.store(c.getProjectId(), file, "CHAT", null, null, u.id());
        var v = media.view(m);
        return new Attachment(m.getId(), m.getFilename(), m.getContentType(), m.getSizeBytes(), v.url());
    }

    @Transactional
    public Map<String, Object> markRead(UUID conversationId, AuthUser u) {
        requireMember(conversationId, u);
        ConversationMember cm = members.findByConversationIdAndUserId(conversationId, u.id()).orElseThrow();
        Instant now = Instant.now();
        cm.setLastReadAt(now);
        members.save(cm);
        List<UUID> others = members.findByConversationId(conversationId).stream().map(ConversationMember::getUserId).filter(id -> !id.equals(u.id())).toList();
        Map<String, Object> push = Map.of("kind", "chat.read", "conversationId", conversationId, "userId", u.id(), "at", now);
        ProjectEvents.afterCommit(() -> hub.toUsers(others, push));
        return Map.of("ok", true);
    }

    // ---- internals
    private Conversation requireMember(UUID conversationId, AuthUser u) {
        Conversation c = conversations.findById(conversationId).orElseThrow(() -> ApiException.notFound("Conversation"));
        if (members.findByConversationIdAndUserId(conversationId, u.id()).isPresent()) return c;
        // project admins and project members that joined after creation get access via project permission
        if (c.getProjectId() != null && access.can(c.getProjectId(), u, Perm.CHAT)) {
            if (!u.isAdmin()) members.save(new ConversationMember(conversationId, u.id()));
            return c;
        }
        throw ApiException.notFound("Conversation");
    }

    private ConversationView view(Conversation c, UUID viewer) {
        List<ConversationMember> ms = members.findByConversationId(c.getId());
        Instant since = ms.stream().filter(m -> m.getUserId().equals(viewer)).map(ConversationMember::getLastReadAt).findFirst().orElse(Instant.EPOCH);
        MessageView last = messages.findFirstByConversationIdOrderByCreatedAtDesc(c.getId()).map(m -> render(c, List.of(m)).get(0)).orElse(null);
        String title = c.getTitle();
        if (title == null) {
            title = ms.stream().map(ConversationMember::getUserId).filter(id -> !id.equals(viewer)).findFirst().flatMap(users::findById).map(User::getFullName).orElse("Chat");
        }
        return new ConversationView(c.getId(), c.getProjectId(), title, ms.stream().map(ConversationMember::getUserId).toList(), last, messages.countUnread(c.getId(), since, viewer));
    }

    private List<MessageView> render(Conversation c, List<Message> list) {
        if (list.isEmpty()) return List.of();
        Map<UUID, User> names = users.findByIdIn(list.stream().map(Message::getSenderId).collect(Collectors.toSet())).stream().collect(Collectors.toMap(User::getId, x -> x));
        List<ConversationMember> ms = members.findByConversationId(c.getId());
        return list.stream().map(m -> {
            boolean read = ms.stream().filter(x -> !x.getUserId().equals(m.getSenderId())).anyMatch(x -> !x.getLastReadAt().isBefore(m.getCreatedAt()));
            Attachment att = null;
            if (m.getMediaId() != null) {
                Media md = media.find(m.getMediaId());
                att = new Attachment(md.getId(), md.getFilename(), md.getContentType(), md.getSizeBytes(), media.view(md).url());
            }
            User s = names.get(m.getSenderId());
            return new MessageView(m.getId(), m.getConversationId(), m.getSenderId(), s == null ? null : s.getFullName(), m.getBody(), att, m.getCreatedAt(), read ? "READ" : "SENT");
        }).toList();
    }
}
