package ee.krerte.cad.gallery;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface GalleryRepository extends JpaRepository<GalleryDesign, Long> {
    Page<GalleryDesign> findByIsPublicTrueOrderByCreatedAtDesc(Pageable pageable);

    Page<GalleryDesign> findByIsPublicTrueOrderByLikesDesc(Pageable pageable);

    @Query(
            "SELECT g FROM GalleryDesign g WHERE g.isPublic = true AND "
                    + "(LOWER(g.title) LIKE LOWER(CONCAT('%', ?1, '%')) OR LOWER(g.tags) LIKE LOWER(CONCAT('%', ?1, '%')))")
    Page<GalleryDesign> search(String query, Pageable pageable);
}
