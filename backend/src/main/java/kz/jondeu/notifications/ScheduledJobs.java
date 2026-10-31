package kz.jondeu.notifications;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import kz.jondeu.payments.PaymentEnums;
import kz.jondeu.payments.PaymentRepository;
import kz.jondeu.stages.Stage;
import kz.jondeu.stages.StageRepository;
import kz.jondeu.stages.StageStatus;
import kz.jondeu.sync.ProjectEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Background processing. Jobs are idempotent (state-based / de-duplicated), so a retry or a second node
 * running the same job cannot create duplicate side effects beyond a benign duplicate evaluation.
 */
@Component
public class ScheduledJobs {
    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);
    private final StageRepository stages;
    private final PaymentRepository payments;
    private final ProjectEvents events;
    private final Notifier notifier;
    private final NotificationRepository notifications;

    public ScheduledJobs(StageRepository stages, PaymentRepository payments, ProjectEvents events, Notifier notifier, NotificationRepository notifications) {
        this.stages = stages; this.payments = payments; this.events = events; this.notifier = notifier; this.notifications = notifications;
    }

    @Scheduled(cron = "0 5 * * * *", zone = "Asia/Almaty")
    @Transactional
    public void markDelayedStages() { markDelayed(LocalDate.now()); }

    @Transactional
    public int markDelayed(LocalDate today) {
        List<Stage> late = stages.findByEndDateBeforeAndStatusIn(today, List.of(StageStatus.IN_PROGRESS, StageStatus.NOT_STARTED));
        for (Stage s : late) {
            s.setStatus(StageStatus.DELAYED);
            stages.save(s);
            events.publish(s.getProjectId(), "STAGE_DELAYED", "stage", s.getId(), null, "Этап просрочен: " + s.getTitle());
            notifier.project(s.getProjectId(), null, "STAGE_DELAYED", "Работы задерживаются", s.getTitle());
        }
        if (!late.isEmpty()) log.info("Marked {} stages as DELAYED", late.size());
        return late.size();
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Almaty")
    @Transactional
    public void paymentReminders() {
        Instant threshold = Instant.now().minus(2, ChronoUnit.DAYS);
        payments.findAll().stream().filter(p -> p.getStatus() == PaymentEnums.Status.REQUESTED && p.getCreatedAt().isBefore(threshold)).forEach(p -> {
            String body = p.getReference() + " " + LocalDate.now();
            if (!notifications.existsByUserIdAndTypeAndProjectIdAndBody(p.getPayerId(), "PAYMENT_DUE", p.getProjectId(), body)) {
                notifier.user(p.getPayerId(), p.getProjectId(), "PAYMENT_DUE", "Напоминание об оплате", body);
            }
        });
    }
}
