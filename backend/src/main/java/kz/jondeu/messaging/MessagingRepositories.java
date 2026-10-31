package kz.jondeu.messaging;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public final class MessagingRepositories {
    private MessagingRepositories() {}

    public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
        Optional<Conversation> findFirstByProjectId(UUID projectId);

        @Query("""
            select c from Conversation c where c.projectId is null and
              c.id in (select m1.conversationId from ConversationMember m1 where m1.userId = :a) and
              c.id in (select m2.conversationId from ConversationMember m2 where m2.userId = :b)""")
        List<Conversation> findDirect(UUID a, UUID b);
    }

    public interface MemberRepository extends JpaRepository<ConversationMember, ConversationMember.Key> {
        List<ConversationMember> findByConversationId(UUID conversationId);
        List<ConversationMember> findByUserId(UUID userId);
        Optional<ConversationMember> findByConversationIdAndUserId(UUID c, UUID u);
    }

    public interface MessageRepository extends JpaRepository<Message, UUID> {
        List<Message> findByConversationIdAndCreatedAtLessThanOrderByCreatedAtDesc(UUID conversationId, Instant before, Pageable p);
        Optional<Message> findFirstByConversationIdOrderByCreatedAtDesc(UUID conversationId);

        @Query("select count(m) from Message m where m.conversationId = :cid and m.createdAt > :since and m.senderId <> :uid")
        long countUnread(UUID cid, Instant since, UUID uid);

        List<Message> findByConversationIdInOrderByCreatedAtDesc(Collection<UUID> ids, Pageable p);
    }
}
