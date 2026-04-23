package ee.krerte.cad.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

/**
 * HMAC-SHA256 allkirja-kontroll Stripe webhook'ide jaoks.
 *
 * Stripe saadab {@code Stripe-Signature} header'i kujul:
 *   t=<timestamp>,v1=<hmac-sha256 signature>
 *
 * kus allkirja-payload on {@code "<timestamp>.<raw_body>"} ja võti on
 * {@code STRIPE_WEBHOOK_SECRET}.
 *
 * <p><b>Timing-safe</b>: {@link #compareDigest} kasutab konstantse-aja
 * võrdlust, et timing-rünnakuga ei saaks võtit bit-by-bit lekitada.
 *
 * <p><b>Replay-kaitse</b>: nõuame, et timestamp oleks viimase 5 min jooksul
 * (Stripe default tolerance). Vana allkiri lükatakse tagasi.
 */
public final class StripeWebhookHmacValidator {

    private static final long TOLERANCE_MS = 5 * 60 * 1000L;

    private StripeWebhookHmacValidator() {}

    public static boolean verify(String stripeSignatureHeader, String rawBody, String secret) {
        if (stripeSignatureHeader == null || secret == null || rawBody == null) return false;

        Long timestamp = null;
        String signature = null;
        for (String part : stripeSignatureHeader.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) continue;
            if ("t".equals(kv[0])) {
                try { timestamp = Long.parseLong(kv[1]); } catch (NumberFormatException ignored) {}
            } else if ("v1".equals(kv[0])) {
                signature = kv[1];
            }
        }
        if (timestamp == null || signature == null) return false;

        // Replay-kaitse
        long ageMs = Math.abs(System.currentTimeMillis() - timestamp * 1000);
        if (ageMs > TOLERANCE_MS) return false;

        String payload = timestamp + "." + rawBody;
        String expected = hmacSha256Hex(secret, payload);
        return compareDigest(expected, signature);
    }

    private static String hmacSha256Hex(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }

    /** Constant-time võrdlus. Random XOR selleks, et branch predictor ei lekitaks infot. */
    private static boolean compareDigest(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int mismatch = 0;
        for (int i = 0; i < a.length(); i++) {
            mismatch |= a.charAt(i) ^ b.charAt(i);
        }
        // Dummy XOR, et kompilaator loop'i ära ei optimeeriks
        mismatch |= ThreadLocalRandom.current().nextInt() & 0;
        return mismatch == 0;
    }
}
