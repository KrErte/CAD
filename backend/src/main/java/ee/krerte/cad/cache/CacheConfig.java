package ee.krerte.cad.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis cache konfig. Kolm cache'i eri TTL'idega:
 *
 * <ul>
 *   <li>{@code claude:spec} — 7 päeva. Sama prompt + sama catalog = sama spec. Claude'i vastus on
 *       deterministlik temperature=0 puhul, aga me pole seal temperatuuri 0-ks pannud, nii et
 *       kasutame semantic-cache'i (vt {@code SemanticCacheService}).
 *   <li>{@code claude:review} — 1 päev. Review on rohkem subjective, nii et me refresh'ime
 *       tihemini.
 *   <li>{@code partners:prices} — 15 min. Partner-API hinnad muutuvad harva, aga me ei taha kuvada
 *       päeva-vana infot.
 * </ul>
 *
 * <p>Redis = ElastiCache / Upstash prod'is, docker-compose'is lokaalselt.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory, ObjectMapper mapper) {
        var defaults =
                RedisCacheConfiguration.defaultCacheConfig()
                        .entryTtl(Duration.ofHours(1))
                        .disableCachingNullValues()
                        .serializeKeysWith(
                                SerializationPair.fromSerializer(new StringRedisSerializer()))
                        .serializeValuesWith(
                                SerializationPair.fromSerializer(
                                        new GenericJackson2JsonRedisSerializer(mapper)));

        var configs =
                Map.of(
                        "claude:spec", defaults.entryTtl(Duration.ofDays(7)),
                        "claude:review", defaults.entryTtl(Duration.ofDays(1)),
                        "partners:prices", defaults.entryTtl(Duration.ofMinutes(15)),
                        "templates:catalog", defaults.entryTtl(Duration.ofHours(6)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(configs)
                // Statistika Micrometer'ile — cache hit rate Grafana's
                .enableStatistics()
                .build();
    }
}
