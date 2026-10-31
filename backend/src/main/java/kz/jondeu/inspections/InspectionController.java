package kz.jondeu.inspections;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import kz.jondeu.identity.CurrentUser;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Inspections")
public class InspectionController {
    private final InspectionService svc;

    public InspectionController(InspectionService svc) { this.svc = svc; }

    @GetMapping("/projects/{projectId}/inspections")
    public List<InspectionService.InspectionView> list(@PathVariable UUID projectId) { return svc.list(projectId, CurrentUser.require()); }

    @PostMapping("/projects/{projectId}/inspections")
    public InspectionService.InspectionView create(@PathVariable UUID projectId, @RequestBody InspectionService.InspectionInput in) { return svc.create(projectId, in, CurrentUser.require()); }

    @PatchMapping("/inspections/{id}")
    public InspectionService.InspectionView update(@PathVariable UUID id, @RequestBody InspectionService.InspectionInput in) { return svc.update(id, in, CurrentUser.require()); }
}
