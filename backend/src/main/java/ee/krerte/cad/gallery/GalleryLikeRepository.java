package ee.krerte.cad.gallery;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface GalleryLikeRepository extends JpaRepository<GalleryLike, Long> {
    Optional<GalleryLike> findByGalleryIdAndUserId(Long galleryId, Long userId);
    boolean existsByGalleryIdAndUserId(Long galleryId, Long userId);
}
