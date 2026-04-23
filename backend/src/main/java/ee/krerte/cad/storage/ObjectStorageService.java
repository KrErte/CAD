package ee.krerte.cad.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

/**
 * S3 / MinIO-compatible object storage backend (STL, Gcode, preview-PNG).
 *
 * <p><b>Miks mitte Postgres bytea?</b> STL-id on 100KB - 50MB suurusega binaarid.
 * Postgres'i bytea kasvatab WAL'i tohutu tempoga, tuleb Postgres läbi stream'ida
 * iga download'i jaoks ja backup'id saavad gigabyte'idega. S3 / MinIO lahendab
 * kõik need: replicate, CDN, pre-signed URL download otse kliendile.
 *
 * <p><b>Presigned URL'id</b>: frontend saab backend'ilt lühiealise URL'i
 * (nt 1h) ja download'ib otse MinIO'st — bypass'ib Tomcat'i backend'i täielikult.
 *
 * <p>Key-layout konventsioon:
 * <pre>
 *   designs/&lt;design-id&gt;/&lt;sha256&gt;.stl
 *   designs/&lt;design-id&gt;/preview.png
 *   gcode/&lt;job-id&gt;.gcode
 *   archive/audit_log/&lt;YYYY-MM&gt;.jsonl.gz
 * </pre>
 */
@Service
public class ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorageService.class);

    private final S3Client s3;
    private final S3Presigner presigner;
    private final String bucket;

    public ObjectStorageService(
        S3Client s3,
        S3Presigner presigner,
        @Value("${app.storage.bucket:cad-artifacts}") String bucket
    ) {
        this.s3 = s3;
        this.presigner = presigner;
        this.bucket = bucket;
    }

    /**
     * Laadib fail'i S3'sse. Tagastab ETag'i (sisu hash), mille me salvestame
     * DB'sse, et CDN cache invalidate + download integrity check'idel kasutada.
     */
    public PutResult put(String key, byte[] body, String contentType) {
        var req = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(contentType)
            .contentLength((long) body.length)
            // Server-side encryption on MinIO/S3 — keys managed by provider
            .serverSideEncryption(ServerSideEncryption.AES256)
            .build();
        var resp = s3.putObject(req, RequestBody.fromBytes(body));
        log.debug("S3 put bucket={} key={} size={} etag={}", bucket, key, body.length, resp.eTag());
        return new PutResult(key, resp.eTag(), body.length);
    }

    /** Loeb fail'i tagasi mällu. Ära kasuta suurte fail'ide jaoks — kasuta presigned URL'i. */
    public byte[] get(String key) {
        var req = GetObjectRequest.builder().bucket(bucket).key(key).build();
        return s3.getObject(req, ResponseTransformer.toBytes()).asByteArray();
    }

    /**
     * Genereerib pre-signed GET URL'i, mille frontend saab download'imiseks
     * kasutada. URL on {@code expiresIn} jooksul kasutatav ja sisaldab
     * kryptograafilist allkirja — keegi teine seda kasutada ei saa.
     */
    public String presignedDownloadUrl(String key, Duration expiresIn) {
        var getReq = GetObjectRequest.builder().bucket(bucket).key(key).build();
        var presignReq = GetObjectPresignRequest.builder()
            .signatureDuration(expiresIn)
            .getObjectRequest(getReq)
            .build();
        return presigner.presignGetObject(presignReq).url().toString();
    }

    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    /** Genereerib ainulaadse key disain-STL jaoks. */
    public static String stlKey(long designId) {
        return "designs/" + designId + "/" + UUID.randomUUID() + ".stl";
    }

    public static String previewKey(long designId) {
        return "designs/" + designId + "/preview.png";
    }

    public static String gcodeKey(long jobId) {
        return "gcode/" + jobId + ".gcode";
    }

    public record PutResult(String key, String etag, long sizeBytes) {}
}
