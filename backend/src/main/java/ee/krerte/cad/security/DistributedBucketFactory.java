package ee.krerte.cad.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Kui me jookseme mitut backend-instantsi (k8s HPA), peab rate-limit state olema jagatud — muidu
 * saab user N × limit, kus N = pod count.
 *
 * <p>Sellepärast kasutame bucket4j-redis'e ProxyManager'it: bucket state elab Redis'es, CAS
 * (compare-and-swap) atomic'ute update'idega. Iga pod näeb sama tõde.
 *
 * <p>Profile: {@code rate-limit-distributed} — alles siis, kui Redis on reachable. Single-node'is
 * (dev, CI) kasutame in-memory bucket'e edasi.
 */
@Configuration
@Profile("rate-limit-distributed")
public class DistributedBucketFactory {

    @Bean
    public RedisClient rateLimitRedisClient(
            @Value("${app.redis.url:redis://redis:6379}") String url) {
        return RedisClient.create(url);
    }

    @Bean
    public StatefulRedisConnection<String, byte[]> rateLimitConnection(RedisClient client) {
        RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
        return client.connect(codec);
    }

    @Bean
    public ProxyManager<String> bucketProxyManager(StatefulRedisConnection<String, byte[]> conn) {
        // TTL 1h past window — Redis mälukasutus piiratud
        return LettuceBasedProxyManager.builderFor(conn)
                .withExpirationStrategy(
                        io.github.bucket4j.distributed.ExpirationAfterWriteStrategy
                                .basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(10)))
                .build();
    }

    /** Bucket ehitaja — konfig anonüümsete ja tariffide jaoks sama kujuga. */
    public static BucketConfiguration buildConfig(long capacity, Duration refillPeriod) {
        return BucketConfiguration.builder()
                .addLimit(
                        Bandwidth.builder()
                                .capacity(capacity)
                                .refillGreedy(capacity, refillPeriod)
                                .build())
                .build();
    }
}
