package kz.jondeu.calendar;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kz.jondeu.identity.CurrentUser;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Calendar")
public class CalendarController {
    private final CalendarService svc;

    public CalendarController(CalendarService svc) { this.svc = svc; }

    @GetMapping("/projects/{projectId}/calendar")
    public List<CalendarService.CalendarItem> range(@PathVariable UUID projectId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return svc.range(projectId, from, to, CurrentUser.require());
    }

    @PostMapping("/projects/{projectId}/calendar")
    public CalendarService.CalendarItem create(@PathVariable UUID projectId, @RequestBody CalendarService.EventInput in) { return svc.create(projectId, in, CurrentUser.require()); }

    @PutMapping("/calendar/{id}")
    public CalendarService.CalendarItem update(@PathVariable UUID id, @RequestBody CalendarService.EventInput in) { return svc.update(id, in, CurrentUser.require()); }

    @DeleteMapping("/calendar/{id}")
    public Map<String, Boolean> delete(@PathVariable UUID id) { svc.delete(id, CurrentUser.require()); return Map.of("ok", true); }
}
