package kz.jondeu.masters;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import kz.jondeu.admin.AuditService;
import kz.jondeu.common.ApiException;
import kz.jondeu.identity.AuthUser;
import kz.jondeu.identity.User;
import kz.jondeu.identity.UserRepository;
import kz.jondeu.masters.MasterRepositories.*;
import kz.jondeu.media.MediaService;
import kz.jondeu.notifications.Notifier;
import kz.jondeu.projects.Project;
import kz.jondeu.projects.ProjectRepository;
import kz.jondeu.projects.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MasterService {
    public record MasterView(UUID id, String fullName, String avatarUrl, String city, String district, String specialization, String category, String bio, int experienceYears,
                             BigDecimal rating, int reviewsCount, int completedProjects, BigDecimal hourlyRate, String currency, boolean verified, boolean available, String companyName) {}
    public record ProfileInput(String specialization, String category, String bio, Integer experienceYears, BigDecimal hourlyRate, Boolean available, String district, String companyName) {}
    public record ReviewView(UUID id, UUID projectId, UUID authorId, String authorName, int rating, String body, Instant createdAt) {}
    public record ReviewInput(int rating, String body) {}

    private final MasterProfileRepository profiles;
    private final ReviewRepository reviews;
    private final UserRepository users;
    private final ProjectRepository projects;
    private final AuditService audit;
    private final Notifier notifier;
    private final MediaService media;

    public MasterService(MasterProfileRepository profiles, ReviewRepository reviews, UserRepository users, ProjectRepository projects, AuditService audit, Notifier notifier, MediaService media) {
        this.profiles = profiles; this.reviews = reviews; this.users = users; this.projects = projects; this.audit = audit; this.notifier = notifier; this.media = media;
    }

    @Transactional(readOnly = true)
    public Page<MasterView> search(String category, String city, BigDecimal minRating, BigDecimal maxRate, Integer minExp, Boolean verified, Boolean available, String q, String sort, int page, int size) {
        Sort s = switch (sort == null ? "recommended" : sort) {
            case "rating" -> Sort.by(Sort.Order.desc("rating"), Sort.Order.desc("reviewsCount"));
            case "price" -> Sort.by(Sort.Order.asc("hourlyRate"));
            case "experience" -> Sort.by(Sort.Order.desc("experienceYears"));
            // recommended = verified first, then rating, then completed volume
            default -> Sort.by(Sort.Order.desc("verified"), Sort.Order.desc("rating"), Sort.Order.desc("completedProjects"));
        };
        String like = q == null || q.isBlank() ? null : "%" + q.toLowerCase().trim() + "%";
        Page<MasterProfile> pg = profiles.search(category, city, minRating, maxRate, minExp, verified, available, like, PageRequest.of(page, Math.min(size, 50), s));
        Map<UUID, User> u = users.findByIdIn(pg.getContent().stream().map(MasterProfile::getUserId).toList()).stream().collect(Collectors.toMap(User::getId, x -> x));
        return pg.map(m -> view(m, u.get(m.getUserId())));
    }

    @Transactional(readOnly = true)
    public MasterView get(UUID id) {
        MasterProfile m = profiles.findById(id).orElseThrow(() -> ApiException.notFound("Master"));
        return view(m, users.findById(id).orElseThrow());
    }

    @Transactional
    public MasterView upsertMine(ProfileInput in, AuthUser u) {
        if (!u.role().isProfessional()) throw ApiException.forbidden("Only professionals have a master profile");
        MasterProfile m = profiles.findById(u.id()).orElseGet(() -> { MasterProfile n = new MasterProfile(); n.setUserId(u.id()); n.setSpecialization("—"); n.setCategory("GENERAL_CONSTRUCTION"); return n; });
        if (in.specialization() != null) m.setSpecialization(in.specialization());
        if (in.category() != null) {
            if (!ServiceCategory.valid(in.category())) throw ApiException.bad("INVALID_CATEGORY", "Unknown category");
            m.setCategory(in.category());
        }
        if (in.bio() != null) m.setBio(in.bio());
        if (in.experienceYears() != null) { if (in.experienceYears() < 0 || in.experienceYears() > 70) throw ApiException.bad("VALIDATION_FAILED", "experienceYears out of range"); m.setExperienceYears(in.experienceYears()); }
        if (in.hourlyRate() != null) { if (in.hourlyRate().signum() < 0) throw ApiException.bad("VALIDATION_FAILED", "hourlyRate must not be negative"); m.setHourlyRate(in.hourlyRate().setScale(2, RoundingMode.HALF_UP)); }
        if (in.available() != null) m.setAvailable(in.available());
        if (in.district() != null) m.setDistrict(in.district());
        if (in.companyName() != null) m.setCompanyName(in.companyName());
        profiles.save(m);
        return view(m, users.findById(u.id()).orElseThrow());
    }

    /** Platform verification is separate from customer ratings. */
    @Transactional
    public MasterView verify(UUID id, boolean verified, AuthUser admin) {
        MasterProfile m = profiles.findById(id).orElseThrow(() -> ApiException.notFound("Master"));
        m.setVerified(verified);
        profiles.save(m);
        audit.log(admin.id(), verified ? "MASTER_VERIFIED" : "MASTER_UNVERIFIED", "master", id, null);
        notifier.user(id, null, "VERIFICATION", verified ? "Профиль подтверждён" : "Подтверждение снято", "Платформа JONDEU");
        return view(m, users.findById(id).orElseThrow());
    }

    @Transactional(readOnly = true)
    public List<ReviewView> reviews(UUID masterId) {
        List<Review> list = reviews.findByMasterIdOrderByCreatedAtDesc(masterId);
        Map<UUID, User> names = users.findByIdIn(list.stream().map(Review::getAuthorId).collect(Collectors.toSet())).stream().collect(Collectors.toMap(User::getId, x -> x));
        return list.stream().map(r -> new ReviewView(r.getId(), r.getProjectId(), r.getAuthorId(), names.get(r.getAuthorId()) == null ? null : names.get(r.getAuthorId()).getFullName(), r.getRating(), r.getBody(), r.getCreatedAt())).toList();
    }

    @Transactional
    public ReviewView review(UUID projectId, ReviewInput in, AuthUser u) {
        Project p = projects.findById(projectId).orElseThrow(() -> ApiException.notFound("Project"));
        if (!p.getOwnerId().equals(u.id())) throw ApiException.notFound("Project");
        if (p.getStatus() != ProjectStatus.COMPLETED) throw ApiException.conflict("PROJECT_NOT_COMPLETED", "Reviews are possible only after project completion");
        if (p.getContractorId() == null) throw ApiException.conflict("NO_CONTRACTOR", "Project has no contractor");
        if (in.rating() < 1 || in.rating() > 5) throw ApiException.bad("VALIDATION_FAILED", "rating must be 1..5");
        if (reviews.existsByProjectIdAndAuthorIdAndMasterId(projectId, u.id(), p.getContractorId())) throw ApiException.conflict("REVIEW_EXISTS", "You already reviewed this project");
        Review r = new Review();
        r.setProjectId(projectId); r.setAuthorId(u.id()); r.setMasterId(p.getContractorId()); r.setRating(in.rating());
        r.setBody(in.body() == null ? null : in.body().substring(0, Math.min(in.body().length(), 2000)));
        reviews.save(r);
        reviews.flush();
        profiles.findById(p.getContractorId()).ifPresent(m -> {
            m.setRating(BigDecimal.valueOf(reviews.average(m.getUserId())).setScale(2, RoundingMode.HALF_UP));
            m.setReviewsCount((int) reviews.countByMasterId(m.getUserId()));
            profiles.save(m);
        });
        notifier.user(p.getContractorId(), projectId, "NEW_REVIEW", "Новый отзыв", in.rating() + "★");
        return new ReviewView(r.getId(), projectId, u.id(), u.fullName(), r.getRating(), r.getBody(), r.getCreatedAt());
    }

    @Transactional
    public void incrementCompleted(UUID masterId) {
        profiles.findById(masterId).ifPresent(m -> { m.setCompletedProjects(m.getCompletedProjects() + 1); profiles.save(m); });
    }

    private MasterView view(MasterProfile m, User u) {
        return new MasterView(m.getUserId(), u.getFullName(), u.getAvatarUrl(), u.getCity(), m.getDistrict(), m.getSpecialization(), m.getCategory(), m.getBio(), m.getExperienceYears(),
            m.getRating(), m.getReviewsCount(), m.getCompletedProjects(), m.getHourlyRate(), m.getCurrency(), m.isVerified(), m.isAvailable(), m.getCompanyName());
    }
}
