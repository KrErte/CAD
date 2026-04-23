package ee.krerte.cad.gallery;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DesignVersionRepository extends JpaRepository<DesignVersion, Long> {
    List<DesignVersion> findByDesignIdOrderByVersionDesc(Long designId);

    @Query("SELECT COALESCE(MAX(v.version), 0) FROM DesignVersion v WHERE v.designId = ?1")
    int maxVersion(Long designId);

    Optional<DesignVersion> findByDesignIdAndVersion(Long designId, int version);
}
