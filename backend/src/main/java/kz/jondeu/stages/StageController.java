package kz.jondeu.stages;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kz.jondeu.identity.CurrentUser;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Stages")
public class StageController {
    private final StageService svc;

    public StageController(StageService svc) { this.svc = svc; }

    @GetMapping("/projects/{projectId}/stages")
    public List<StageService.StageView> list(@PathVariable UUID projectId) { return svc.list(projectId, CurrentUser.require()); }

    @PostMapping("/projects/{projectId}/stages")
    public StageService.StageView add(@PathVariable UUID projectId, @RequestBody StageService.StageCreate c) { return svc.add(projectId, c, CurrentUser.require()); }

    @PatchMapping("/stages/{id}")
    public StageService.StageView update(@PathVariable UUID id, @RequestBody StageService.StageUpdate r) { return svc.update(id, r, CurrentUser.require()); }

    @GetMapping("/stages/{id}/comments")
    public List<StageService.CommentView> comments(@PathVariable UUID id) { return svc.comments(id, CurrentUser.require()); }

    @PostMapping("/stages/{id}/comments")
    public StageService.CommentView comment(@PathVariable UUID id, @RequestBody Map<String, String> body) { return svc.comment(id, body.get("body"), CurrentUser.require()); }
}
