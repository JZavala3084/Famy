package com.famy.shared.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Configuración propia de Famy (prefijo {@code famy}). */
@ConfigurationProperties("famy")
public record FamyProperties(
        @DefaultValue("http://localhost:4200") String baseUrl,
        @DefaultValue Cors cors,
        @DefaultValue Jwt jwt,
        @DefaultValue Google google,
        @DefaultValue Mail mail,
        @DefaultValue Auth auth,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue("./data") String dataDir,
        @DefaultValue Push push) {

    public record Cors(@DefaultValue("http://localhost:4200") List<String> allowedOrigins) {
    }

    /**
     * @param privateKeyFile PEM PKCS#8 con la clave EC P-256 que firma los access tokens (ES256).
     * @param requireKey     si es {@code true} y no hay archivo, la API no arranca (producción).
     */
    public record Jwt(
            String privateKeyFile,
            @DefaultValue("famy") String issuer,
            @DefaultValue("15m") Duration accessTokenTtl,
            @DefaultValue("30d") Duration refreshTokenTtl,
            @DefaultValue("false") boolean requireKey,
            @DefaultValue("true") boolean secureCookie) {
    }

    /** Client ids de Google aceptados como {@code aud} del id_token (web, Android, iOS). */
    public record Google(@DefaultValue({}) List<String> clientIds) {
    }

    /** @param mode {@code smtp} envía correos reales; {@code log} solo los escribe en el log (desarrollo). */
    public record Mail(@DefaultValue("Famy <no-reply@famy.local>") String from, @DefaultValue("log") String mode) {
    }

    public record Auth(
            @DefaultValue("24h") Duration verifyTokenTtl,
            @DefaultValue("30m") Duration resetTokenTtl,
            @DefaultValue("7d") Duration invitationTtl) {
    }

    public record RateLimit(@DefaultValue("true") boolean enabled) {
    }

    /**
     * Push web (VAPID, RFC 8292).
     *
     * @param vapidPrivateKeyFile PEM PKCS#8 con la clave EC P-256; si no existe se genera y se guarda (por defecto en
     *                            {@code dataDir/vapid-private.pem}) para que las suscripciones sobrevivan reinicios
     * @param subject             contacto que ven los servicios de push ({@code mailto:} o URL)
     */
    public record Push(
            @DefaultValue("true") boolean enabled,
            String vapidPrivateKeyFile,
            @DefaultValue("mailto:admin@famy.local") String subject) {
    }
}
