package com.famy.identity.application;

import com.famy.identity.domain.AppUser;
import com.famy.identity.domain.EmailToken;
import com.famy.identity.domain.EmailToken.Purpose;
import com.famy.identity.infrastructure.EmailTokenRepository;
import com.famy.identity.infrastructure.mail.EmailSender;
import com.famy.shared.SecureTokens;
import com.famy.shared.config.FamyProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Emite, envía y consume tokens de correo de un solo uso (verificación y recuperación). */
@Service
class EmailTokenService {

    private static final Logger log = LoggerFactory.getLogger(EmailTokenService.class);

    private final EmailTokenRepository tokens;
    private final EmailSender sender;
    private final FamyProperties props;

    EmailTokenService(EmailTokenRepository tokens, EmailSender sender, FamyProperties props) {
        this.tokens = tokens;
        this.sender = sender;
        this.props = props;
    }

    void sendVerification(AppUser user) {
        String raw = issue(user, Purpose.VERIFY);
        String link = props.baseUrl() + "/verify-email?token=" + encode(raw);
        send(user.getEmail(), "Confirma tu correo en Famy", """
                Hola %s,

                Confirma tu correo para empezar a usar Famy:
                %s

                El enlace vence en %d horas. Si no creaste una cuenta, ignora este mensaje.
                """.formatted(user.getDisplayName(), link, props.auth().verifyTokenTtl().toHours()));
    }

    void sendPasswordReset(AppUser user) {
        String raw = issue(user, Purpose.RESET);
        String link = props.baseUrl() + "/reset-password?token=" + encode(raw);
        send(user.getEmail(), "Restablece tu contraseña de Famy", """
                Hola %s,

                Recibimos una solicitud para restablecer tu contraseña:
                %s

                El enlace vence en %d minutos y solo puede usarse una vez.
                Si no fuiste tú, ignora este mensaje: tu contraseña no cambia.
                """.formatted(user.getDisplayName(), link, props.auth().resetTokenTtl().toMinutes()));
    }

    void sendAlreadyRegistered(AppUser user) {
        send(user.getEmail(), "Ya tienes una cuenta en Famy", """
                Hola %s,

                Alguien intentó crear una cuenta en Famy con este correo, pero ya tienes una.
                Inicia sesión en %s o restablece tu contraseña si la olvidaste.
                Si no fuiste tú, ignora este mensaje.
                """.formatted(user.getDisplayName(), props.baseUrl()));
    }

    /** Consume el token si es válido; lo marca como usado. */
    Optional<EmailToken> consume(String raw, Purpose purpose) {
        if (raw == null || raw.isBlank() || raw.length() > 200) {
            return Optional.empty();
        }
        return tokens.findByTokenHashAndPurpose(SecureTokens.sha256(raw), purpose)
                .filter(t -> t.isUsable(Instant.now()))
                .map(t -> {
                    t.markUsed();
                    return t;
                });
    }

    @Scheduled(cron = "0 23 3 * * *")
    @Transactional
    public void purgeExpired() {
        tokens.deleteExpiredBefore(Instant.now().minus(7, ChronoUnit.DAYS));
    }

    private String issue(AppUser user, Purpose purpose) {
        Instant now = Instant.now();
        tokens.invalidateAll(user.getId(), purpose, now);
        String raw = SecureTokens.newToken();
        var ttl = purpose == Purpose.VERIFY ? props.auth().verifyTokenTtl() : props.auth().resetTokenTtl();
        tokens.save(new EmailToken(user.getId(), purpose, SecureTokens.sha256(raw), now.plus(ttl)));
        return raw;
    }

    private void send(String to, String subject, String body) {
        try {
            sender.send(to, subject, body);
        } catch (RuntimeException e) {
            // No se revela al cliente (evita enumeración); queda en el log para el administrador.
            log.error("No se pudo enviar el correo '{}'", subject, e);
        }
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
