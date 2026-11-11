package com.famy.identity.application;

import com.famy.identity.domain.AppUser;
import com.famy.identity.domain.EmailToken.Purpose;
import com.famy.identity.domain.UserIdentity;
import com.famy.identity.infrastructure.AppUserRepository;
import com.famy.identity.infrastructure.UserIdentityRepository;
import com.famy.identity.infrastructure.google.GoogleIdTokenVerifier;
import com.famy.identity.infrastructure.google.GoogleIdTokenVerifier.GoogleUser;
import com.famy.shared.audit.AuditService;
import com.famy.shared.error.ApiException;
import com.famy.shared.ratelimit.RateLimiter;
import com.famy.shared.ratelimit.RateLimiter.Policy;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso de autenticación: registro, verificación, login (correo y Google) y contraseñas. */
@Service
public class AuthService {

    /** Bloqueo progresivo por cuenta: 5 fallos en 15 minutos bloquean el login de ese correo. */
    static final Policy LOGIN_FAILURES = new Policy("login-failures", 5, Duration.ofMinutes(15));

    private final AppUserRepository users;
    private final UserIdentityRepository identities;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final EmailTokenService emailTokens;
    private final TokenService tokens;
    private final GoogleIdTokenVerifier google;
    private final RateLimiter rateLimiter;
    private final AuditService audit;
    /** Hash ficticio para igualar tiempos cuando el correo no existe (evita enumeración por tiempo). */
    private final String dummyHash;

    public AuthService(AppUserRepository users, UserIdentityRepository identities, PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy, EmailTokenService emailTokens, TokenService tokens,
            GoogleIdTokenVerifier google, RateLimiter rateLimiter, AuditService audit) {
        this.users = users;
        this.identities = identities;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.emailTokens = emailTokens;
        this.tokens = tokens;
        this.google = google;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.dummyHash = passwordEncoder.encode("famy-dummy-password-for-timing");
    }

    public record RegisterCommand(String email, String password, String displayName, String timezone,
            String locale) {
    }

    /**
     * Registra con correo y contraseña y envía el enlace de verificación. Responde igual exista o no el correo:
     * si ya existe, se avisa al dueño por correo en lugar de revelarlo en la respuesta.
     */
    @Transactional
    public void register(RegisterCommand cmd) {
        String email = AppUser.normalizeEmail(cmd.email());
        passwordPolicy.validate(cmd.password(), email);
        String hash = passwordEncoder.encode(cmd.password());
        Optional<AppUser> existing = users.findByEmail(email);
        if (existing.isPresent()) {
            AppUser user = existing.get();
            if (user.isEmailVerified() || !user.hasPassword()) {
                emailTokens.sendAlreadyRegistered(user);
            } else {
                emailTokens.sendVerification(user);
            }
            return;
        }
        AppUser user = new AppUser(email, cmd.displayName(), validTimezone(cmd.timezone()),
                cmd.locale() == null || cmd.locale().isBlank() ? "es" : cmd.locale());
        user.changePasswordHash(hash);
        users.save(user);
        audit.record("auth.register", user.getId());
        emailTokens.sendVerification(user);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        var token = emailTokens.consume(rawToken, Purpose.VERIFY)
                .orElseThrow(() -> ApiException.badRequest("invalid_token", "El enlace no es válido o ya venció."));
        AppUser user = users.findById(token.getUserId()).orElseThrow();
        user.markEmailVerified();
        audit.record("auth.email_verified", user.getId());
    }

    @Transactional
    public void resendVerification(String email) {
        users.findByEmail(AppUser.normalizeEmail(email))
                .filter(u -> !u.isEmailVerified() && u.isActive())
                .ifPresent(emailTokens::sendVerification);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public TokenPair login(String email, String password, String deviceName) {
        String normalized = AppUser.normalizeEmail(email);
        if (rateLimiter.isExhausted(LOGIN_FAILURES, normalized)) {
            throw ApiException.tooManyRequests();
        }
        Optional<AppUser> user = users.findByEmail(normalized);
        String hash = user.filter(AppUser::hasPassword).map(AppUser::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(password, hash);
        if (user.isEmpty() || !user.get().hasPassword() || !matches || !user.get().isActive()) {
            rateLimiter.tryConsume(LOGIN_FAILURES, normalized);
            audit.record("auth.login_failed", user.map(AppUser::getId).orElse(null));
            throw ApiException.unauthorized("invalid_credentials", "Correo o contraseña incorrectos.");
        }
        AppUser u = user.get();
        rateLimiter.reset(LOGIN_FAILURES, normalized);
        if (passwordEncoder.upgradeEncoding(u.getPasswordHash())) {
            u.changePasswordHash(passwordEncoder.encode(password));
        }
        audit.record("auth.login", u.getId(), null, Map.of("method", "password"));
        return tokens.issue(u, deviceName);
    }

    /**
     * Login o registro con Google. Si el correo ya existe se vincula la identidad. Si esa cuenta nunca verificó
     * su correo, se elimina su contraseña y se cierran sus sesiones: así nadie que haya registrado el correo de
     * otra persona antes que ella conserva acceso (pre-account hijacking).
     */
    @Transactional
    public TokenPair loginWithGoogle(String idToken, String deviceName) {
        if (!google.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "google_not_configured",
                    "El inicio de sesión con Google no está configurado.");
        }
        GoogleUser g = google.verify(idToken)
                .orElseThrow(() -> ApiException.unauthorized("invalid_google_token", "No se pudo validar tu cuenta de Google."));
        Optional<UserIdentity> identity = identities.findByProviderAndProviderSubject(UserIdentity.GOOGLE, g.subject());
        AppUser user;
        if (identity.isPresent()) {
            user = users.findById(identity.get().getUserId()).orElseThrow();
        } else {
            if (!g.emailVerified() || g.email() == null) {
                throw ApiException.unauthorized("google_email_not_verified",
                        "Tu cuenta de Google no tiene el correo verificado.");
            }
            String email = AppUser.normalizeEmail(g.email());
            user = users.findByEmail(email).orElse(null);
            if (user == null) {
                user = new AppUser(email, g.name() == null || g.name().isBlank() ? email.split("@")[0] : g.name(),
                        "UTC", "es");
                user.updateProfile(null, g.picture(), null, null, null, null, null);
                user.markEmailVerified();
                users.save(user);
                audit.record("auth.register", user.getId(), null, Map.of("method", "google"));
            } else if (!user.isEmailVerified()) {
                user.changePasswordHash(null);
                user.markEmailVerified();
                tokens.revokeAll(user.getId());
            }
            identities.save(new UserIdentity(user.getId(), UserIdentity.GOOGLE, g.subject(), email));
            audit.record("auth.identity_linked", user.getId(), null, Map.of("provider", UserIdentity.GOOGLE));
        }
        if (!user.isActive()) {
            throw ApiException.unauthorized("invalid_credentials", "La cuenta está desactivada.");
        }
        audit.record("auth.login", user.getId(), null, Map.of("method", "google"));
        return tokens.issue(user, deviceName);
    }

    @Transactional
    public void forgotPassword(String email) {
        users.findByEmail(AppUser.normalizeEmail(email))
                .filter(AppUser::isActive)
                .ifPresent(emailTokens::sendPasswordReset);
    }

    /** Restablece la contraseña con el token del correo y cierra todas las sesiones. */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        var token = emailTokens.consume(rawToken, Purpose.RESET)
                .orElseThrow(() -> ApiException.badRequest("invalid_token", "El enlace no es válido o ya venció."));
        AppUser user = users.findById(token.getUserId()).orElseThrow();
        passwordPolicy.validate(newPassword, user.getEmail());
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        // Quien recibe el correo demuestra que es dueño del correo.
        user.markEmailVerified();
        tokens.revokeAll(user.getId());
        audit.record("auth.password_reset", user.getId());
    }

    /** Cambia (o crea, si la cuenta solo usaba Google) la contraseña y cierra las demás sesiones. */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        AppUser user = users.findById(userId).orElseThrow();
        if (user.hasPassword() && (currentPassword == null
                || !passwordEncoder.matches(currentPassword, user.getPasswordHash()))) {
            throw ApiException.badRequest("invalid_current_password", "La contraseña actual no es correcta.");
        }
        passwordPolicy.validate(newPassword, user.getEmail());
        user.changePasswordHash(passwordEncoder.encode(newPassword));
        tokens.revokeAll(userId);
        audit.record("auth.password_changed", userId);
    }

    static String validTimezone(String tz) {
        if (tz == null || tz.isBlank()) {
            return "UTC";
        }
        try {
            return ZoneId.of(tz).getId();
        } catch (RuntimeException e) {
            throw ApiException.badRequest("invalid_timezone", "Zona horaria no válida.");
        }
    }
}
