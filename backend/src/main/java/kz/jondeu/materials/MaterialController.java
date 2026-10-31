package kz.jondeu.materials;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import kz.jondeu.identity.CurrentUser;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Materials")
public class MaterialController {
    private final MaterialService svc;

    public MaterialController(MaterialService svc) { this.svc = svc; }

    @GetMapping("/projects/{projectId}/materials")
    public MaterialService.MaterialsResponse list(@PathVariable UUID projectId) { return svc.list(projectId, CurrentUser.require()); }

    @PostMapping("/projects/{projectId}/materials")
    public MaterialService.MaterialView create(@PathVariable UUID projectId, @RequestBody MaterialService.MaterialInput in) { return svc.create(projectId, in, CurrentUser.require()); }

    @PutMapping("/materials/{id}")
    public MaterialService.MaterialView update(@PathVariable UUID id, @RequestBody MaterialService.MaterialInput in) { return svc.update(id, in, CurrentUser.require()); }

    @PostMapping("/materials/{id}/order")
    public MaterialService.MaterialView order(@PathVariable UUID id) { return svc.order(id, CurrentUser.require()); }

    @PostMapping("/materials/{id}/receive")
    public MaterialService.MaterialView receive(@PathVariable UUID id, @RequestBody MaterialService.ReceiveInput in) { return svc.receive(id, in, CurrentUser.require()); }

    @PostMapping("/materials/{id}/cancel")
    public MaterialService.MaterialView cancel(@PathVariable UUID id) { return svc.cancel(id, CurrentUser.require()); }
}
