package kz.jondeu.masters;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/catalog")
@Tag(name = "Catalog")
public class CatalogController {
    @GetMapping("/categories")
    public List<Map<String, String>> categories() { return ServiceCategory.all(); }

    @GetMapping("/cities")
    public List<Map<String, String>> cities() {
        return List.of(Map.of("code", "Almaty", "ru", "Алматы", "kk", "Алматы", "en", "Almaty"),
            Map.of("code", "Astana", "ru", "Астана", "kk", "Астана", "en", "Astana"),
            Map.of("code", "Shymkent", "ru", "Шымкент", "kk", "Шымкент", "en", "Shymkent"));
    }
}
