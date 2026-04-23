package ee.krerte.cad.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@tehisaicad.ee}")
    private String fromAddress;

    @Value("${app.mail.enabled:false}")
    private boolean enabled;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendWelcome(String toEmail, String name) {
        String subject = "Tere tulemast TehisAI CAD-i!";
        String body =
                """
            <div style="font-family:sans-serif;max-width:600px;margin:0 auto;color:#e2e8f0;background:#0f172a;padding:2rem;border-radius:12px">
              <h1 style="color:#a5b4fc">Tere, %s!</h1>
              <p>Oled nüüd TehisAI CAD kasutaja. Sul on <strong>3 tasuta STL-genereerimist kuus</strong>.</p>
              <p>Alusta kohe:</p>
              <a href="%s" style="display:inline-block;background:#6366f1;color:white;padding:.8rem 1.5rem;border-radius:8px;text-decoration:none;font-weight:600">
                Ava TehisAI CAD →
              </a>
              <hr style="border-color:#1e293b;margin:2rem 0">
              <p style="color:#94a3b8;font-size:.85rem">
                Mida saad teha:<br>
                • Kirjelda eesti keeles mida vajad<br>
                • Kohanda parameetreid reaalajas<br>
                • Lae STL alla ja prindi
              </p>
              <p style="color:#94a3b8;font-size:.85rem">
                Vajad rohkem? <a href="%s/#pricing" style="color:#a5b4fc">Vaata pakette</a>
              </p>
              <p style="color:#64748b;font-size:.75rem;margin-top:2rem">
                TehisAI CAD · tehisaicad.ee · See kiri saadeti, kuna registreerusid meie teenusesse.
              </p>
            </div>
            """
                        .formatted(name != null ? name : "kasutaja", frontendUrl, frontendUrl);
        send(toEmail, subject, body);
    }

    @Async
    public void sendQuotaWarning(String toEmail, String name, int used, int limit) {
        String subject = "Sinu tasuta piir on peaaegu täis";
        String body =
                """
            <div style="font-family:sans-serif;max-width:600px;margin:0 auto;color:#e2e8f0;background:#0f172a;padding:2rem;border-radius:12px">
              <h2 style="color:#fbbf24">⚠️ %d/%d STL-i kasutatud sel kuul</h2>
              <p>Tere, %s! Oled kasutanud %d oma %d tasuta STL-genereerimisest sel kuul.</p>
              <p>Piiramatuks genereerimiseks uuenda Hobi paketile — vaid <strong>4.99 €/kuu</strong>.</p>
              <a href="%s/#pricing" style="display:inline-block;background:#6366f1;color:white;padding:.8rem 1.5rem;border-radius:8px;text-decoration:none;font-weight:600">
                Uuenda →
              </a>
              <p style="color:#64748b;font-size:.75rem;margin-top:2rem">
                TehisAI CAD · tehisaicad.ee
              </p>
            </div>
            """
                        .formatted(
                                used,
                                limit,
                                name != null ? name : "kasutaja",
                                used,
                                limit,
                                frontendUrl);
        send(toEmail, subject, body);
    }

    @Async
    public void sendQuotaExhausted(String toEmail, String name) {
        String subject = "Tasuta piir on sel kuul täis";
        String body =
                """
            <div style="font-family:sans-serif;max-width:600px;margin:0 auto;color:#e2e8f0;background:#0f172a;padding:2rem;border-radius:12px">
              <h2 style="color:#ef4444">Tasuta STL-id on sel kuul otsas</h2>
              <p>Tere, %s! Oled kasutanud kõik tasuta genereerimised sel kuul.</p>
              <p>Sul on kaks varianti:</p>
              <ul style="color:#cbd5e1">
                <li>Oota järgmise kuu algust (piir uueneb automaatselt)</li>
                <li>Uuenda Hobi paketile — <strong>piiramatult</strong> genereerimisi</li>
              </ul>
              <a href="%s/#pricing" style="display:inline-block;background:#6366f1;color:white;padding:.8rem 1.5rem;border-radius:8px;text-decoration:none;font-weight:600">
                Uuenda →
              </a>
              <p style="color:#64748b;font-size:.75rem;margin-top:2rem">
                TehisAI CAD · tehisaicad.ee
              </p>
            </div>
            """
                        .formatted(name != null ? name : "kasutaja", frontendUrl);
        send(toEmail, subject, body);
    }

    private void send(String to, String subject, String htmlBody) {
        if (!enabled) {
            log.info("Email disabled, would send to={} subject={}", to, subject);
            return;
        }
        try {
            var msg = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(msg);
            log.info("Email sent to={} subject={}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to={}: {}", to, e.getMessage());
        }
    }
}
