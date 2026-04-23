package ee.krerte.cad.printflow.repo;

import ee.krerte.cad.printflow.entity.OrganizationMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, Long> {
    List<OrganizationMember> findByUserId(Long userId);

    List<OrganizationMember> findByOrganizationId(Long organizationId);

    Optional<OrganizationMember> findByOrganizationIdAndUserId(Long orgId, Long userId);
}
