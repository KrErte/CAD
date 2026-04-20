package ee.krerte.cad.printflow.service;

import ee.krerte.cad.auth.User;
import ee.krerte.cad.auth.UserRepository;
import ee.krerte.cad.printflow.entity.Organization;
import ee.krerte.cad.printflow.entity.OrganizationMember;
import ee.krerte.cad.printflow.repo.OrganizationMemberRepository;
import ee.krerte.cad.printflow.repo.OrganizationRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

/**
 * Lahendab päringu jooksvalt aktiivse organisatsiooni (tenant).
 *
 * V1: vaikimisi kasutaja esimene organisatsioon. V2-s tuleb header
 * `X-Org-Id`-ga switching.
 */
@Component
public class OrganizationContext {

    private final UserRepository userRepo;
    private final OrganizationRepository orgRepo;
    private final OrganizationMemberRepository memberRepo;

    public OrganizationContext(UserRepository u, OrganizationRepository o, OrganizationMemberRepository m) {
        this.userRepo = u;
        this.orgRepo = o;
        this.memberRepo = m;
    }

    /** Jooksev kasutaja JwtAuthFilter-ist (seab Principal = email). */
    public User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "pole sisse loginud");
        }
        String email = auth.getName();
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "tundmatu kasutaja"));
    }

    /** Leia (või loo) vaikimisi organisation praeguse kasutaja jaoks. */
    @Transactional
    public Organization currentOrganization() {
        User u = currentUser();
        return resolveForUser(u);
    }

    @Transactional
    public Organization resolveForUser(User u) {
        List<OrganizationMember> memberships = memberRepo.findByUserId(u.getId());
        if (!memberships.isEmpty()) {
            Long orgId = memberships.get(0).getOrganizationId();
            return orgRepo.findById(orgId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "org kaotsi"));
        }

        // Kasutajal pole ühtegi org-i (nt uus kasutaja) → loome SOLO vaikimisi
        Organization org = new Organization();
        org.setName(u.getName() != null ? u.getName() + "'s Workshop" : "My Workshop");
        org.setSlug("workshop-" + u.getId());
        org.setPlan("SOLO");
        org.setOwnerUserId(u.getId());
        org = orgRepo.save(org);

        OrganizationMember m = new OrganizationMember();
        m.setOrganizationId(org.getId());
        m.setUserId(u.getId());
        m.setRole("OWNER");
        m.setAcceptedAt(java.time.Instant.now());
        memberRepo.save(m);
        return org;
    }

    /** Kas kasutaja on rollis (või kõrgemas)? OWNER > ADMIN > OPERATOR > VIEWER */
    public boolean hasRole(Long orgId, Long userId, String minimumRole) {
        Optional<OrganizationMember> m = memberRepo.findByOrganizationIdAndUserId(orgId, userId);
        if (m.isEmpty()) return false;
        return rank(m.get().getRole()) >= rank(minimumRole);
    }

    public void requireRole(String minimumRole) {
        User u = currentUser();
        Organization o = resolveForUser(u);
        if (!hasRole(o.getId(), u.getId(), minimumRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "nõuab rolli vähemalt " + minimumRole);
        }
    }

    private int rank(String role) {
        return switch (role == null ? "VIEWER" : role) {
            case "OWNER" -> 4;
            case "ADMIN" -> 3;
            case "OPERATOR" -> 2;
            case "VIEWER" -> 1;
            default -> 0;
        };
    }
}
