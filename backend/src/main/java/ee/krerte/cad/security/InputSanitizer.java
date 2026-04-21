package ee.krerte.cad.security;

import java.util.regex.Pattern;

/**
 * Lihtsad input-sanitiseerimise abifunktsioonid. Meeldetuletus: parameetrite
 * validation peaks esimese kaitse olema Bean Validation (@NotBlank, @Size,
 * @Pattern) controller DTO-del — see siin on TEINE kaitse-kiht potentsiaalselt
 * kahtlase input'i jaoks (kasutaja-prompt mis läheb Claude'i, fail-nimed mis
 * sattuvad disk'i, jne).
 */
public final class InputSanitizer {

    /** Maksimaalne kasutaja-prompti pikkus ilma Claude'i saatmata. */
    public static final int MAX_PROMPT_LEN = 4000;

    /** Filename-safe pattern — A-Za-z0-9._- ja pikkus kuni 100. */
    private static final Pattern SAFE_FILENAME = Pattern.compile("[A-Za-z0-9._-]{1,100}");

    /** SSRF-ohtlikud hostinimed ja ranged. */
    private static final Pattern BLOCKED_HOSTS = Pattern.compile(
        "^(localhost|127\\.|0\\.|10\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.|192\\.168\\.|169\\.254\\.|::1|fc00:|fe80:).*",
        Pattern.CASE_INSENSITIVE
    );

    private InputSanitizer() {}

    /**
     * Piirame pikkuse ja trimmime NUL-byte'id. Claude API kasutab JSON
     * encoding'ut, nii et me ei pea escape'ima — piisab pikkuse piirangust.
     */
    public static String truncatePrompt(String input) {
        if (input == null) return "";
        String cleaned = input.replace("\0", "").strip();
        if (cleaned.length() > MAX_PROMPT_LEN) {
            return cleaned.substring(0, MAX_PROMPT_LEN);
        }
        return cleaned;
    }

    /**
     * Kontrolli, kas failinimi on ohutu (ei sisalda path traversal'i jne).
     * Lükka tagasi: "../etc/passwd", "foo/bar.txt", "C:\\windows", tühjad nimed.
     */
    public static boolean isSafeFilename(String name) {
        return name != null && SAFE_FILENAME.matcher(name).matches();
    }

    /**
     * SSRF-kaitse URL-idele, mida kasutajale ei ole otse antud.
     * Kasutame välja-minevate URL-ide (nt partner-API-d) valideerimiseks.
     */
    public static boolean isPublicHttpUrl(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            var uri = java.net.URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equals("http") || scheme.equals("https"))) return false;
            String host = uri.getHost();
            if (host == null) return false;
            return !BLOCKED_HOSTS.matcher(host).matches();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
