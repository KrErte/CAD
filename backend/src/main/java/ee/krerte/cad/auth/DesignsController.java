package ee.krerte.cad.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/designs")
public class DesignsController {

    private final DesignRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();

    public DesignsController(DesignRepository repo) { this.repo = repo; }

    private Long uid() {
        return (Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return repo.findByUserIdOrderByCreatedAtDesc(uid(), PageRequest.of(0, 50))
                .stream().map(d -> Map.<String, Object>of(
                        "id", d.getId(),
                        "template", d.getTemplate(),
                        "summary_et", d.getSummaryEt() == null ? "" : d.getSummaryEt(),
                        "params", safeJson(d.getParams()),
                        "size_bytes", d.getSizeBytes(),
                        "created_at", d.getCreatedAt().toString()
                )).toList();
    }

    @GetMapping("/{id}/stl")
    public ResponseEntity<?> stl(@PathVariable Long id) {
        return repo.findById(id)
                .filter(d -> d.getUserId().equals(uid()))
                .map(d -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("application/sla"))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + d.getTemplate() + "-" + d.getId() + ".stl\"")
                        .body((Object) d.getStl()))
                .orElseGet(() -> ResponseEntity.status(404).build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        return repo.findById(id)
                .filter(d -> d.getUserId().equals(uid()))
                .map(d -> { repo.delete(d); return ResponseEntity.noContent().build(); })
                .orElseGet(() -> ResponseEntity.status(404).build());
    }

    private JsonNode safeJson(String s) {
        try { return mapper.readTree(s); } catch (Exception e) { return mapper.createObjectNode(); }
    }
}
