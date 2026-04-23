package ee.krerte.cad.gallery;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GalleryLikeRepository extends JpaRepository<GalleryLike, Long> {
    Optional<GalleryLike> findByGalleryIdAndUserId(Long galleryId, Long userId);

    boolean existsByGalleryIdAndUserId(Long galleryId, Long userId);
}
