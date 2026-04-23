package ee.krerte.cad.gallery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ee.krerte.cad.auth.Design;
import ee.krerte.cad.auth.DesignRepository;
import ee.krerte.cad.auth.UserRepository;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/gallery")
public class GalleryController {

    private final GalleryRepository galleryRepo;
    private final GalleryLikeRepository likeRepo;
    private final DesignRepository designRepo;
    private final UserRepository userRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public GalleryController(
            GalleryRepository galleryRepo,
            GalleryLikeRepository likeRepo,
            DesignRepository designRepo,
            UserRepository userRepo) {
        this.galleryRepo = galleryRepo;
        this.likeRepo = likeRepo;
        this.designRepo = designRepo;
        this.userRepo = userRepo;
    }

    private Long uid() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return null;
        try {
            return (Long) auth.getPrincipal();
        } catch (ClassCastException e) {
            return null;
        }
    }

    /** Browse gallery — public, paginated, sortable. No auth required. */
    @GetMapping
    public List<Map<String, Object>> browse(
            @RequestParam(defaultValue = "new") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "") String q) {

        var pageable = PageRequest.of(page, 20);
        var results =
                q.isBlank()
                        ? ("popular".equals(sort)
                                ? galleryRepo.findByIsPublicTrueOrderByLikesDesc(pageable)
                                : galleryRepo.findByIsPublicTrueOrderByCreatedAtDesc(pageable))
                        : galleryRepo.search(q, pageable);

        Long myId = uid();
        return results.stream()
                .map(
                        g -> {
                            var design = designRepo.findById(g.getDesignId()).orElse(null);
                            var author = userRepo.findById(g.getUserId()).orElse(null);
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("id", g.getId());
                            m.put("title", g.getTitle());
                            m.put("description", g.getDescription());
                            m.put("tags", g.getTags());
                            m.put("likes", g.getLikes());
                            m.put("forks", g.getForks());
                            m.put("author", author != null ? author.getName() : "Anonüümne");
                            m.put("template", design != null ? design.getTemplate() : "");
                            m.put("params", design != null ? safeJson(design.getParams()) : null);
                            m.put("created_at", g.getCreatedAt().toString());
                            m.put(
                                    "liked_by_me",
                                    myId != null
                                            && likeRepo.existsByGalleryIdAndUserId(
                                                    g.getId(), myId));
                            return m;
                        })
                .toList();
    }

    /** Share a design to the gallery. */
    public record ShareRequest(Long designId, String title, String description, String tags) {}

    @PostMapping("/share")
    public ResponseEntity<?> share(@RequestBody ShareRequest req) {
        Long userId = uid();
        if (userId == null) return ResponseEntity.status(401).build();

        var design = designRepo.findById(req.designId()).orElse(null);
        if (design == null || !design.getUserId().equals(userId)) {
            return ResponseEntity.status(404).body(Map.of("error", "Disaini ei leitud"));
        }

        GalleryDesign g = new GalleryDesign();
        g.setDesignId(req.designId());
        g.setUserId(userId);
        g.setTitle(req.title() != null ? req.title() : design.getTemplate());
        g.setDescription(req.description());
        g.setTags(req.tags());
        galleryRepo.save(g);
        return ResponseEntity.ok(Map.of("id", g.getId(), "message", "Jagatud galeriisse!"));
    }

    /** Like/unlike a gallery design (toggle). */
    @PostMapping("/{id}/like")
    public ResponseEntity<?> toggleLike(@PathVariable Long id) {
        Long userId = uid();
        if (userId == null) return ResponseEntity.status(401).build();

        var gallery = galleryRepo.findById(id).orElse(null);
        if (gallery == null) return ResponseEntity.status(404).build();

        var existing = likeRepo.findByGalleryIdAndUserId(id, userId);
        if (existing.isPresent()) {
            likeRepo.delete(existing.get());
            gallery.setLikes(Math.max(0, gallery.getLikes() - 1));
            galleryRepo.save(gallery);
            return ResponseEntity.ok(Map.of("liked", false, "likes", gallery.getLikes()));
        } else {
            GalleryLike like = new GalleryLike();
            like.setGalleryId(id);
            like.setUserId(userId);
            likeRepo.save(like);
            gallery.setLikes(gallery.getLikes() + 1);
            galleryRepo.save(gallery);
            return ResponseEntity.ok(Map.of("liked", true, "likes", gallery.getLikes()));
        }
    }

    /** Fork a gallery design into user's own designs. */
    @PostMapping("/{id}/fork")
    public ResponseEntity<?> fork(@PathVariable Long id) {
        Long userId = uid();
        if (userId == null) return ResponseEntity.status(401).build();

        var gallery = galleryRepo.findById(id).orElse(null);
        if (gallery == null) return ResponseEntity.status(404).build();

        var original = designRepo.findById(gallery.getDesignId()).orElse(null);
        if (original == null) return ResponseEntity.status(404).build();

        Design fork = new Design();
        fork.setUserId(userId);
        fork.setTemplate(original.getTemplate());
        fork.setParams(original.getParams());
        fork.setSummaryEt("Fork: " + gallery.getTitle());
        fork.setStl(original.getStl());
        designRepo.save(fork);

        gallery.setForks(gallery.getForks() + 1);
        galleryRepo.save(gallery);

        return ResponseEntity.ok(Map.of("design_id", fork.getId(), "message", "Disain forkitud!"));
    }

    /** Get STL for a gallery design (public download). */
    @GetMapping("/{id}/stl")
    public ResponseEntity<?> stl(@PathVariable Long id) {
        var gallery = galleryRepo.findById(id).orElse(null);
        if (gallery == null || !gallery.isPublic()) return ResponseEntity.status(404).build();

        var design = designRepo.findById(gallery.getDesignId()).orElse(null);
        if (design == null) return ResponseEntity.status(404).build();

        return ResponseEntity.ok()
                .header("Content-Type", "application/sla")
                .header(
                        "Content-Disposition",
                        "attachment; filename=\"" + gallery.getTitle() + ".stl\"")
                .body(design.getStl());
    }

    private JsonNode safeJson(String s) {
        try {
            return mapper.readTree(s);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }
}
