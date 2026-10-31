package kz.jondeu.payments;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import kz.jondeu.identity.CurrentUser;
import kz.jondeu.payments.PaymentEnums.Method;
import kz.jondeu.payments.PaymentEnums.Type;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Collections;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Payments")
public class PaymentController {
    public record PayRequest(Type type, Method method, BigDecimal amount, UUID stageId, String note, String idempotencyKey) {}
    private final PaymentService svc;

    public PaymentController(PaymentService svc) { this.svc = svc; }

    @GetMapping("/projects/{projectId}/payments")
    public List<PaymentService.PaymentView> list(@PathVariable UUID projectId) { return svc.list(projectId, CurrentUser.require()); }

    /** Idempotency-Key header (or body field) is mandatory: retries with the same key never double-charge. */
    @PostMapping("/projects/{projectId}/payments")
    public PaymentService.PaymentView pay(@PathVariable UUID projectId, @RequestHeader(value = "Idempotency-Key", required = false) String key, @RequestBody PayRequest r) {
        return svc.pay(projectId, new PaymentService.PayCommand(r.type(), r.method(), r.amount(), r.stageId(), r.note(), key != null ? key : r.idempotencyKey()), CurrentUser.require());
    }

    @PostMapping("/projects/{projectId}/payment-requests")
    public PaymentService.PaymentView request(@PathVariable UUID projectId, @RequestBody PaymentService.RequestCommand r) { return svc.requestPayment(projectId, r, CurrentUser.require()); }

    @PostMapping("/payments/{id}/pay")
    public PaymentService.PaymentView payRequested(@PathVariable UUID id, @RequestBody Map<String, Method> body) { return svc.payRequested(id, body.get("method"), CurrentUser.require()); }

    @PostMapping("/payments/{id}/cancel")
    public PaymentService.PaymentView cancel(@PathVariable UUID id) { return svc.cancel(id, CurrentUser.require()); }

    @PostMapping("/payments/{id}/confirm")
    public PaymentService.PaymentView confirm(@PathVariable UUID id) { return svc.confirmManual(id, CurrentUser.require()); }

    @PostMapping("/payments/{id}/refund")
    public PaymentService.PaymentView refund(@PathVariable UUID id) { return svc.refund(id, CurrentUser.require()); }

    /** DEV ONLY (404 when sandbox disabled). */
    @PostMapping("/payments/{id}/sandbox-resolve")
    public PaymentService.PaymentView sandbox(@PathVariable UUID id, @RequestBody Map<String, Boolean> body) { return svc.sandboxResolve(id, Boolean.TRUE.equals(body.get("success")), CurrentUser.require()); }

    @PostMapping("/payments/callbacks/{provider}")
    public PaymentService.PaymentView callback(@PathVariable String provider, @RequestBody String rawBody, HttpServletRequest req) {
        Map<String, String> headers = Collections.list(req.getHeaderNames()).stream().collect(Collectors.toMap(String::toLowerCase, req::getHeader, (a, b) -> a));
        return svc.handleCallback(provider, headers, rawBody);
    }
}
