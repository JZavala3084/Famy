package com.famy.identity.infrastructure.google;

import com.famy.shared.config.FamyProperties;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/** Valida id_tokens de Google contra sus JWKs públicas. */
@Component
class NimbusGoogleIdTokenVerifier implements GoogleIdTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(NimbusGoogleIdTokenVerifier.class);
    private static final String JWKS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final List<String> clientIds;
    private volatile NimbusJwtDecoder decoder;

    NimbusGoogleIdTokenVerifier(FamyProperties props) {
        this.clientIds = props.google().clientIds().stream().filter(s -> !s.isBlank()).toList();
    }

    @Override
    public boolean isConfigured() {
        return !clientIds.isEmpty();
    }

    @Override
    public Optional<GoogleUser> verify(String idToken) {
        try {
            Jwt jwt = decoder().decode(idToken);
            Object verified = jwt.getClaims().get("email_verified");
            boolean emailVerified = Boolean.TRUE.equals(verified) || "true".equals(verified);
            return Optional.of(new GoogleUser(jwt.getSubject(), jwt.getClaimAsString("email"), emailVerified,
                    jwt.getClaimAsString("name"), jwt.getClaimAsString("picture")));
        } catch (JwtException e) {
            log.debug("id_token de Google inválido: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private NimbusJwtDecoder decoder() {
        if (decoder == null) {
            synchronized (this) {
                if (decoder == null) {
                    NimbusJwtDecoder d = NimbusJwtDecoder.withJwkSetUri(JWKS).build();
                    OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                            new JwtTimestampValidator(),
                            new JwtClaimValidator<Object>("iss", iss -> iss != null && ISSUERS.contains(iss.toString())),
                            new JwtClaimValidator<List<String>>("aud",
                                    aud -> aud != null && aud.stream().anyMatch(clientIds::contains)));
                    d.setJwtValidator(validator);
                    decoder = d;
                }
            }
        }
        return decoder;
    }
}
