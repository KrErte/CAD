package ee.krerte.cad.gallery;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "gallery_designs")
public class GalleryDesign {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "design_id", nullable = false)
    private Long designId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String title;

    private String description;
    private String tags;

    @Column(nullable = false)
    private int likes = 0;

    @Column(nullable = false)
    private int forks = 0;

    @Column(name = "public", nullable = false)
    private boolean isPublic = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    // --- Transient fields for API responses ---
    @Transient private String authorName;
    @Transient private String template;
    @Transient private String params;
    @Transient private boolean likedByMe;

    public Long getId() { return id; }
    public Long getDesignId() { return designId; }
    public void setDesignId(Long d) { this.designId = d; }
    public Long getUserId() { return userId; }
    public void setUserId(Long u) { this.userId = u; }
    public String getTitle() { return title; }
    public void setTitle(String t) { this.title = t; }
    public String getDescription() { return description; }
    public void setDescription(String d) { this.description = d; }
    public String getTags() { return tags; }
    public void setTags(String t) { this.tags = t; }
    public int getLikes() { return likes; }
    public void setLikes(int l) { this.likes = l; }
    public int getForks() { return forks; }
    public void setForks(int f) { this.forks = f; }
    public boolean isPublic() { return isPublic; }
    public void setPublic(boolean p) { this.isPublic = p; }
    public Instant getCreatedAt() { return createdAt; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String a) { this.authorName = a; }
    public String getTemplate() { return template; }
    public void setTemplate(String t) { this.template = t; }
    public String getParams() { return params; }
    public void setParams(String p) { this.params = p; }
    public boolean isLikedByMe() { return likedByMe; }
    public void setLikedByMe(boolean l) { this.likedByMe = l; }
}
