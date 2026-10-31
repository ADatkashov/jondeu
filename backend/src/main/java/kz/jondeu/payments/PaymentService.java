package kz.jondeu.payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kz.jondeu.admin.AuditService;
import kz.jondeu.common.ApiException;
import kz.jondeu.common.Money;
import kz.jondeu.identity.AuthUser;
import kz.jondeu.notifications.Notifier;
import kz.jondeu.payments.PaymentEnums.*;
import kz.jondeu.projects.*;
import kz.jondeu.projects.ProjectRole.Perm;
import kz.jondeu.sync.ProjectEvents;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    public static final String MANUAL = "MANUAL";

    public record PayCommand(Type type, Method method, BigDecimal amount, UUID stageId, String note, String idempotencyKey) {}
    public record RequestCommand(Type type, BigDecimal amount, UUID stageId, String note) {}
    public record PaymentView(UUID id, UUID projectId, UUID stageId, UUID payerId, UUID requestedBy, Type type, Method method, BigDecimal amount, String currency,
                              Status status, String provider, String providerRef, String reference, String note, Instant createdAt, Instant paidAt, boolean sandbox) {
        static PaymentView of(Payment p) {
            return new PaymentView(p.getId(), p.getProjectId(), p.getStageId(), p.getPayerId(), p.getRequestedBy(), p.getType(), p.getMethod(), p.getAmount(), p.getCurrency(),
                p.getStatus(), p.getProvider(), p.getProviderRef(), p.getReference(), p.getNote(), p.getCreatedAt(), p.getPaidAt(), SandboxPaymentProvider.NAME.equals(p.getProvider()));
        }
    }

    private final PaymentRepository payments;
    private final LedgerRepository ledger;
    private final FinanceService finance;
    private final ProjectAccess access;
    private final ProjectRepository projects;
    private final List<PaymentProvider> providers;
    private final SandboxPaymentProvider sandbox;
    private final ProjectEvents events;
    private final Notifier notifier;
    private final AuditService audit;
    private final ObjectMapper mapper;

    public PaymentService(PaymentRepository payments, LedgerRepository ledger, FinanceService finance, ProjectAccess access, ProjectRepository projects,
                          List<PaymentProvider> providers, SandboxPaymentProvider sandbox, ProjectEvents events, Notifier notifier, AuditService audit, ObjectMapper mapper) {
        this.payments = payments; this.ledger = ledger; this.finance = finance; this.access = access; this.projects = projects; this.providers = providers;
        this.sandbox = sandbox; this.events = events; this.notifier = notifier; this.audit = audit; this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<PaymentView> list(UUID projectId, AuthUser u) {
        access.require(projectId, u, Perm.VIEW);
        return payments.findByProjectIdOrderByCreatedAtDesc(projectId).stream().map(PaymentView::of).toList();
    }

    @Transactional
    public PaymentView pay(UUID projectId, PayCommand c, AuthUser u) {
        if (c.idempotencyKey() == null || c.idempotencyKey().isBlank() || c.idempotencyKey().length() > 100) {
            throw ApiException.bad("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key is required (max 100 chars)");
        }
        var replay = payments.findByIdempotencyKey(c.idempotencyKey());
        if (replay.isPresent()) {
            Payment e = replay.get();
            if (!e.getProjectId().equals(projectId) || !e.getPayerId().equals(u.id())) throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "Idempotency key was used for a different operation");
            return PaymentView.of(e);
        }
        Project p = access.requireLocked(projectId, u, Perm.PAY);
        // re-check after taking the lock: a concurrent identical request may have just committed
        var again = payments.findByIdempotencyKey(c.idempotencyKey());
        if (again.isPresent()) return PaymentView.of(again.get());
        Payment pay = base(p, c.type(), c.amount(), c.stageId(), c.note());
        pay.setPayerId(u.id());
        pay.setIdempotencyKey(c.idempotencyKey());
        initiate(p, pay, c.method());
        payments.save(pay);
        events.publish(projectId, "PAYMENT_CREATED", "payment", pay.getId(), u.id(), "Платёж " + pay.getReference() + " на " + pay.getAmount() + " " + pay.getCurrency() + " создан");
        return PaymentView.of(pay);
    }

    /** Contractor asks the customer for a payment (advance / stage / material). Not reserved until the customer pays. */
    @Transactional
    public PaymentView requestPayment(UUID projectId, RequestCommand c, AuthUser u) {
        Project p = access.requireLocked(projectId, u, Perm.REQUEST_PAYMENT);
        Payment pay = base(p, c.type(), c.amount(), c.stageId(), c.note());
        pay.setPayerId(p.getOwnerId()); pay.setRequestedBy(u.id());
        pay.setStatus(Status.REQUESTED); pay.setMethod(Method.CARD); pay.setProvider(MANUAL);
        pay.setIdempotencyKey("req-" + UUID.randomUUID());
        payments.save(pay);
        events.publish(projectId, "PAYMENT_REQUESTED", "payment", pay.getId(), u.id(), "Запрос оплаты " + pay.getAmount() + " " + pay.getCurrency());
        notifier.user(p.getOwnerId(), projectId, "PAYMENT_DUE", "Запрос на оплату", pay.getAmount() + " " + pay.getCurrency() + (c.note() == null ? "" : " — " + c.note()));
        return PaymentView.of(pay);
    }

    @Transactional
    public PaymentView payRequested(UUID paymentId, Method method, AuthUser u) {
        Payment pay = load(paymentId);
        Project p = access.requireLocked(pay.getProjectId(), u, Perm.PAY);
        pay = load(paymentId);
        if (pay.getStatus() == Status.PENDING || pay.getStatus() == Status.PAID) return PaymentView.of(pay); // idempotent replay
        if (pay.getStatus() != Status.REQUESTED) throw ApiException.conflict("INVALID_TRANSITION", "Payment is " + pay.getStatus());
        BigDecimal available = finance.position(p.getId(), p.getApprovedBudget()).available();
        if (pay.getAmount().compareTo(available) > 0) throw ApiException.unprocessable("EXCEEDS_OUTSTANDING", "Amount exceeds the outstanding balance (" + available + ")");
        initiate(p, pay, method);
        payments.save(pay);
        events.publish(p.getId(), "PAYMENT_CREATED", "payment", pay.getId(), u.id(), "Платёж " + pay.getReference() + " инициирован");
        return PaymentView.of(pay);
    }

    @Transactional
    public PaymentView cancel(UUID paymentId, AuthUser u) {
        Payment pay = load(paymentId);
        Project p = access.requireLocked(pay.getProjectId(), u, Perm.VIEW);
        pay = load(paymentId);
        boolean mine = u.id().equals(pay.getPayerId()) || u.id().equals(pay.getRequestedBy()) || u.isAdmin();
        if (!mine) throw ApiException.forbidden("Only the payer or the requester can cancel");
        if (pay.getStatus() != Status.REQUESTED && pay.getStatus() != Status.PENDING) throw ApiException.conflict("INVALID_TRANSITION", "Payment is " + pay.getStatus());
        pay.setStatus(Status.CANCELLED);
        payments.save(pay);
        events.publish(p.getId(), "PAYMENT_CANCELLED", "payment", pay.getId(), u.id(), "Платёж " + pay.getReference() + " отменён");
        return PaymentView.of(pay);
    }

    /** Cash / bank transfer: the receiving side (contractor) confirms they actually got the money. */
    @Transactional
    public PaymentView confirmManual(UUID paymentId, AuthUser u) {
        Payment pay = load(paymentId);
        Project p = access.requireLocked(pay.getProjectId(), u, Perm.REQUEST_PAYMENT);
        pay = load(paymentId);
        if (!MANUAL.equals(pay.getProvider()) || pay.getStatus() != Status.PENDING) throw ApiException.conflict("INVALID_TRANSITION", "Only a pending cash/bank-transfer payment can be confirmed manually");
        markPaid(p, pay, u.id());
        audit.log(u.id(), "PAYMENT_MANUAL_CONFIRMED", "payment", pay.getId(), pay.getReference());
        return PaymentView.of(pay);
    }

    @Transactional
    public PaymentView refund(UUID paymentId, AuthUser u) {
        Payment pay = load(paymentId);
        Project p = access.requireLocked(pay.getProjectId(), u, Perm.REQUEST_PAYMENT);
        pay = load(paymentId);
        if (pay.getStatus() != Status.PAID) throw ApiException.conflict("INVALID_TRANSITION", "Only a paid payment can be refunded");
        pay.setStatus(Status.REFUNDED);
        payments.save(pay);
        addLedger(pay, LedgerEntry.Kind.REFUND, u.id(), "Refund " + pay.getReference());
        audit.log(u.id(), "PAYMENT_REFUNDED", "payment", pay.getId(), pay.getReference() + " " + pay.getAmount());
        events.publish(p.getId(), "PAYMENT_REFUNDED", "payment", pay.getId(), u.id(), "Возврат по платежу " + pay.getReference());
        notifier.project(p.getId(), u.id(), "PAYMENT_REFUNDED", "Возврат средств", pay.getAmount() + " " + pay.getCurrency());
        return PaymentView.of(pay);
    }

    /** Public endpoint entrypoint. Authenticity is established by the provider's signature check. */
    @Transactional
    public PaymentView handleCallback(String providerName, Map<String, String> headers, String rawBody) {
        PaymentProvider prov = providers.stream().filter(x -> x.name().equalsIgnoreCase(providerName)).findFirst()
            .orElseThrow(() -> ApiException.notFound("Provider"));
        var result = prov.verifyCallback(headers, rawBody);
        Payment pay = payments.findByProviderAndProviderRef(prov.name(), result.providerRef()).orElseThrow(() -> ApiException.notFound("Payment"));
        Project p = projects.lockById(pay.getProjectId()).orElseThrow();
        pay = load(pay.getId());
        if (pay.getStatus() == Status.PAID || pay.getStatus() == Status.FAILED || pay.getStatus() == Status.REFUNDED) return PaymentView.of(pay); // replayed callback
        if (pay.getStatus() != Status.PENDING) throw ApiException.conflict("INVALID_TRANSITION", "Payment is " + pay.getStatus());
        if (result.success()) {
            markPaid(p, pay, null);
        } else {
            pay.setStatus(Status.FAILED);
            payments.save(pay);
            events.publish(p.getId(), "PAYMENT_FAILED", "payment", pay.getId(), null, "Платёж " + pay.getReference() + " не прошёл");
            notifier.user(pay.getPayerId(), p.getId(), "PAYMENT_FAILED", "Платёж не прошёл", pay.getReference());
        }
        return PaymentView.of(pay);
    }

    /** Dev/demo only: emulates the gateway by sending a correctly SIGNED callback through the real verification path. */
    @Transactional
    public PaymentView sandboxResolve(UUID paymentId, boolean success, AuthUser u) {
        if (!sandbox.available()) throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Sandbox is disabled");
        Payment pay = load(paymentId);
        access.require(pay.getProjectId(), u, Perm.PAY);
        if (!SandboxPaymentProvider.NAME.equals(pay.getProvider())) throw ApiException.conflict("NOT_SANDBOX", "Payment was not created with the sandbox provider");
        String body;
        try {
            body = mapper.writeValueAsString(Map.of("providerRef", pay.getProviderRef(), "status", success ? "SUCCESS" : "FAILED"));
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        return handleCallback(SandboxPaymentProvider.NAME, Map.of("x-sandbox-signature", sandbox.sign(body)), body);
    }

    // ----- internals
    private Payment base(Project p, Type type, BigDecimal amount, UUID stageId, String note) {
        if (type == null) throw ApiException.bad("VALIDATION_FAILED", "type is required");
        if (amount == null || amount.signum() <= 0) throw ApiException.bad("VALIDATION_FAILED", "amount must be positive");
        if (amount.scale() > 2 && amount.stripTrailingZeros().scale() > 2) throw ApiException.bad("VALIDATION_FAILED", "amount has more than 2 decimal places");
        if (p.getApprovedBudget() == null || p.getApprovedBudget().signum() <= 0) throw ApiException.conflict("NO_APPROVED_ESTIMATE", "Payments are possible only after an estimate is approved");
        if (p.getStatus() == ProjectStatus.CANCELLED) throw ApiException.conflict("PROJECT_STATE", "Project is cancelled");
        BigDecimal amt = Money.scale(amount);
        BigDecimal available = finance.position(p.getId(), p.getApprovedBudget()).available();
        if (amt.compareTo(available) > 0) throw ApiException.unprocessable("EXCEEDS_OUTSTANDING", "Amount exceeds the outstanding balance (" + available + ")");
        Payment pay = new Payment();
        pay.setProjectId(p.getId()); pay.setType(type); pay.setAmount(amt); pay.setCurrency(p.getCurrency()); pay.setStageId(stageId); pay.setNote(note);
        pay.setReference("PAY-" + java.time.Year.now() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        return pay;
    }

    private void initiate(Project p, Payment pay, Method method) {
        if (method == null) throw ApiException.bad("VALIDATION_FAILED", "method is required");
        pay.setMethod(method);
        if (method == Method.CASH || method == Method.BANK_TRANSFER) {
            pay.setProvider(MANUAL);
            pay.setStatus(Status.PENDING);
            return;
        }
        PaymentProvider prov = providers.stream().filter(x -> x.supports(method) && x.available()).findFirst()
            .orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_NOT_CONFIGURED", "No payment provider is configured for " + method));
        var r = prov.initiate(pay.getReference(), pay.getAmount(), pay.getCurrency(), "JONDEU " + p.getName());
        pay.setProvider(prov.name()); pay.setProviderRef(r.providerRef()); pay.setStatus(Status.PENDING);
    }

    private void markPaid(Project p, Payment pay, UUID actor) {
        // final guard: never let the ledger exceed the approved budget
        BigDecimal net = finance.netPaid(p.getId());
        if (net.add(pay.getAmount()).compareTo(p.getApprovedBudget()) > 0) {
            pay.setStatus(Status.FAILED);
            payments.save(pay);
            throw ApiException.unprocessable("EXCEEDS_OUTSTANDING", "Payment would exceed the approved budget");
        }
        pay.setStatus(Status.PAID); pay.setPaidAt(Instant.now());
        payments.save(pay);
        addLedger(pay, LedgerEntry.Kind.PAYMENT, actor, "Payment " + pay.getReference());
        events.publish(p.getId(), "PAYMENT_RECEIVED", "payment", pay.getId(), actor, "Получен платёж " + pay.getAmount() + " " + pay.getCurrency() + " (" + pay.getReference() + ")");
        notifier.project(p.getId(), actor, "PAYMENT_RECEIVED", "Платёж получен", pay.getAmount() + " " + pay.getCurrency());
    }

    private void addLedger(Payment pay, LedgerEntry.Kind kind, UUID actor, String note) {
        LedgerEntry l = new LedgerEntry();
        l.setProjectId(pay.getProjectId()); l.setPaymentId(pay.getId()); l.setKind(kind); l.setAmount(pay.getAmount());
        l.setCurrency(pay.getCurrency()); l.setActorId(actor); l.setNote(note);
        ledger.save(l);
    }

    private Payment load(UUID id) { return payments.findById(id).orElseThrow(() -> ApiException.notFound("Payment")); }
}
