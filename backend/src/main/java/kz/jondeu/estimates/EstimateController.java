package kz.jondeu.estimates;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kz.jondeu.identity.CurrentUser;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Estimates")
public class EstimateController {
    private final EstimateService svc;
    private final EstimatePdfService pdf;

    public EstimateController(EstimateService svc, EstimatePdfService pdf) { this.svc = svc; this.pdf = pdf; }

    @GetMapping("/projects/{projectId}/estimates")
    public List<EstimateService.EstimateView> list(@PathVariable UUID projectId) { return svc.list(projectId, CurrentUser.require()); }

    @PostMapping("/projects/{projectId}/estimates")
    public EstimateService.EstimateView create(@PathVariable UUID projectId, @RequestBody EstimateService.EstimateInput in) { return svc.create(projectId, in, CurrentUser.require()); }

    @GetMapping("/estimates/{id}")
    public EstimateService.EstimateView get(@PathVariable UUID id) { return svc.get(id, CurrentUser.require()); }

    @PutMapping("/estimates/{id}")
    public EstimateService.EstimateView update(@PathVariable UUID id, @RequestBody EstimateService.EstimateInput in) { return svc.update(id, in, CurrentUser.require()); }

    @PostMapping("/estimates/{id}/submit")
    public EstimateService.EstimateView submit(@PathVariable UUID id) { return svc.submit(id, CurrentUser.require()); }

    @PostMapping("/estimates/{id}/approve")
    public EstimateService.EstimateView approve(@PathVariable UUID id) { return svc.approve(id, CurrentUser.require()); }

    @PostMapping("/estimates/{id}/reject")
    public EstimateService.EstimateView reject(@PathVariable UUID id, @RequestBody(required = false) Map<String, String> body) {
        return svc.reject(id, body == null ? null : body.get("comment"), false, CurrentUser.require());
    }

    @PostMapping("/estimates/{id}/request-changes")
    public EstimateService.EstimateView requestChanges(@PathVariable UUID id, @RequestBody(required = false) Map<String, String> body) {
        return svc.reject(id, body == null ? null : body.get("comment"), true, CurrentUser.require());
    }

    @PostMapping("/estimates/{id}/revise")
    public EstimateService.EstimateView revise(@PathVariable UUID id) { return svc.revise(id, CurrentUser.require()); }

    @GetMapping(value = "/estimates/{id}/pdf", produces = "application/pdf")
    public org.springframework.http.ResponseEntity<byte[]> pdf(@PathVariable UUID id) {
        var view = svc.get(id, CurrentUser.require());
        return org.springframework.http.ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"" + view.number() + "-v" + view.version() + ".pdf\"")
            .body(pdf.render(view));
    }
}
