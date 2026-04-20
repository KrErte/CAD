package ee.krerte.cad.gallery;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "gallery_likes", uniqueConstraints = @UniqueConstraint(columnNames = {"gallery_id", "user_id"}))
public class GalleryLike {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gallery_id", nullable = false)
    private Long galleryId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public Long getGalleryId() { return galleryId; }
    public void setGalleryId(Long g) { this.galleryId = g; }
    public Long getUserId() { return userId; }
    public void setUserId(Long u) { this.userId = u; }
}
