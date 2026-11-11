package com.famy.shared.ratelimit;

import com.famy.shared.config.FamyProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Limitador de peticiones en memoria (Bucket4j) — suficiente para una sola instancia en el homelab.
 * Cada clave (p. ej. {@code login:ip:1.2.3.4}) tiene su propio bucket.
 */
@Component
public class RateLimiter {

    /** Una política: {@code capacity} operaciones por ventana {@code period}. */
    public record Policy(String name, int capacity, Duration period) {
    }

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(1))
            .maximumSize(100_000)
            .build();
    private final boolean enabled;

    public RateLimiter(FamyProperties props) {
        this.enabled = props.rateLimit().enabled();
    }

    /** Consume una ficha; devuelve {@code false} si se superó el límite. */
    public boolean tryConsume(Policy policy, String key) {
        if (!enabled) {
            return true;
        }
        return bucket(policy, key).tryConsume(1);
    }

    /** Indica si la clave ya agotó su límite, sin consumir. */
    public boolean isExhausted(Policy policy, String key) {
        if (!enabled) {
            return false;
        }
        return bucket(policy, key).getAvailableTokens() <= 0;
    }

    public void reset(Policy policy, String key) {
        buckets.invalidate(policy.name() + ":" + key);
    }

    private Bucket bucket(Policy policy, String key) {
        return buckets.get(policy.name() + ":" + key, k -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(policy.capacity())
                        .refillGreedy(policy.capacity(), policy.period())
                        .build())
                .build());
    }
}
