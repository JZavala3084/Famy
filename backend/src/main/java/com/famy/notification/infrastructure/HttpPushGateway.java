package com.famy.notification.infrastructure;

import com.famy.shared.config.FamyProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Implementación con {@link HttpClient}: cifra (RFC 8291), firma VAPID (RFC 8292) y hace el POST. */
@Component
class HttpPushGateway implements PushGateway {

    private static final Logger log = LoggerFactory.getLogger(HttpPushGateway.class);
    private static final Duration JWT_TTL = Duration.ofHours(12);

    private final VapidKeys vapid;
    private final String subject;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    /** JWT VAPID por origen, reutilizado hasta una hora antes de vencer. */
    private final Map<String, CachedJwt> jwts = new ConcurrentHashMap<>();

    private record CachedJwt(String token, Instant expiresAt) {
    }

    HttpPushGateway(VapidKeys vapid, FamyProperties props) {
        this.vapid = vapid;
        this.subject = props.push().subject();
    }

    @Override
    public Result send(Target target, byte[] payload, int ttlSeconds, String topic) {
        URI endpoint;
        try {
            endpoint = URI.create(target.endpoint());
            if (!"https".equals(endpoint.getScheme())) {
                return Result.GONE;
            }
        } catch (IllegalArgumentException e) {
            return Result.GONE;
        }
        try {
            byte[] body = WebPushCrypto.encrypt(payload, WebPushCrypto.unb64(target.p256dh()),
                    WebPushCrypto.unb64(target.auth()));
            HttpRequest.Builder req = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/octet-stream")
                    .header("Content-Encoding", "aes128gcm")
                    .header("TTL", Integer.toString(ttlSeconds))
                    .header("Urgency", "normal")
                    .header("Authorization", "vapid t=" + jwt(endpoint) + ", k=" + vapid.publicKey())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body));
            if (topic != null) {
                req.header("Topic", topic);
            }
            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            int status = res.statusCode();
            if (status >= 200 && status < 300) {
                return Result.DELIVERED;
            }
            if (status == 404 || status == 410) {
                return Result.GONE;
            }
            log.warn("Push rechazado por {} ({}): {}", endpoint.getHost(), status,
                    res.body().length() > 200 ? res.body().substring(0, 200) : res.body());
            // 403: la suscripción se creó con otra clave VAPID; no volverá a funcionar.
            return status == 403 ? Result.GONE : Result.FAILED;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.warn("Suscripción push inválida para {}: {}", endpoint.getHost(), e.getMessage());
            return Result.GONE;
        } catch (IOException e) {
            log.warn("No se pudo enviar el push a {}: {}", endpoint.getHost(), e.getMessage());
            return Result.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.FAILED;
        }
    }

    private String jwt(URI endpoint) throws GeneralSecurityException {
        String audience = endpoint.getScheme() + "://" + endpoint.getAuthority();
        Instant now = Instant.now();
        CachedJwt cached = jwts.get(audience);
        if (cached != null && cached.expiresAt().minus(Duration.ofHours(1)).isAfter(now)) {
            return cached.token();
        }
        Instant exp = now.plus(JWT_TTL);
        String token = WebPushCrypto.vapidJwt(audience, subject, exp.getEpochSecond(), vapid.keyPair().getPrivate());
        jwts.put(audience, new CachedJwt(token, exp));
        return token;
    }
}
