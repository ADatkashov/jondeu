package kz.jondeu.masters;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import kz.jondeu.identity.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Masters")
public class MasterController {
    private final MasterService svc;

    public MasterController(MasterService svc) { this.svc = svc; }

    @GetMapping("/masters")
    public Page<MasterService.MasterView> search(@RequestParam(required = false) String category, @RequestParam(required = false) String city, @RequestParam(required = false) BigDecimal minRating,
                                                 @RequestParam(required = false) BigDecimal maxRate, @RequestParam(required = false) Integer minExperience, @RequestParam(required = false) Boolean verified,
                                                 @RequestParam(required = false) Boolean available, @RequestParam(required = false) String q, @RequestParam(required = false) String sort,
                                                 @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return svc.search(category, city, minRating, maxRate, minExperience, verified, available, q, sort, page, size);
    }

    @GetMapping("/masters/{id}")
    public MasterService.MasterView get(@PathVariable UUID id) { return svc.get(id); }

    @GetMapping("/masters/{id}/reviews")
    public List<MasterService.ReviewView> reviews(@PathVariable UUID id) { return svc.reviews(id); }

    @PutMapping("/masters/me")
    public MasterService.MasterView upsert(@RequestBody MasterService.ProfileInput in) { return svc.upsertMine(in, CurrentUser.require()); }

    @PostMapping("/projects/{projectId}/reviews")
    public MasterService.ReviewView review(@PathVariable UUID projectId, @RequestBody MasterService.ReviewInput in) { return svc.review(projectId, in, CurrentUser.require()); }
}
