package ee.krerte.cad.auth;

import ee.krerte.cad.mail.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository users;
    private final JwtService jwt;
    private final EmailService emailService;
    private final String frontendUrl;

    public OAuth2SuccessHandler(
            UserRepository users,
            JwtService jwt,
            EmailService emailService,
            @Value("${app.frontend-url:http://localhost:4200}") String frontendUrl) {
        this.users = users;
        this.jwt = jwt;
        this.emailService = emailService;
        this.frontendUrl = frontendUrl;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest req, HttpServletResponse res, Authentication auth)
            throws IOException {
        OAuth2User principal = (OAuth2User) auth.getPrincipal();
        String sub = principal.getAttribute("sub");
        String email = principal.getAttribute("email");
        String name = principal.getAttribute("name");

        boolean isNew = false;
        User u =
                users.findByGoogleSub(sub)
                        .orElseGet(() -> users.findByEmail(email).orElseGet(User::new));
        if (u.getId() == null) isNew = true;
        u.setGoogleSub(sub);
        u.setEmail(email);
        if (u.getName() == null) u.setName(name);
        users.save(u);

        if (isNew) {
            emailService.sendWelcome(email, name);
        }

        String token = jwt.issue(u);
        res.sendRedirect(frontendUrl + "/#/auth?token=" + token);
    }
}
