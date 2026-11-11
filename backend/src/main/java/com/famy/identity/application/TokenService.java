package com.famy.identity.application;

import com.famy.identity.AccessTokenClaimsProvider;
import com.famy.identity.AccessTokenClaimsProvider.FamilyClaims;
import com.famy.identity.domain.AppUser;
import com.famy.identity.domain.RefreshToken;
import com.famy.identity.infrastructure.AppUserRepository;
import com.famy.identity.infrastructure.RefreshTokenRepository;
import com.famy.shared.Ids;
import com.famy.shared.SecureTokens;
import com.famy.shared.audit.AuditService;
import com.famy.shared.config.FamyProperties;
import com.famy.shared.error.ApiException;
import com.famy.shared.web.ClientInfo;
import com.nimbusds.jose.jwk.ECKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emite access tokens (JWT ES256, 15 min) y refresh tokens opacos (30 días) con rotación y detección de
 * reutilización: si llega un refresh token que ya fue rotado, se revoca toda su cadena.
 */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final ECKey jwk;
    private final RefreshTokenRepository refreshTokens;
    private final AppUserRepository users;
    private final ObjectProvider<AccessTokenClaimsProvider> claimsProvider;
    private final AuditService audit;
    private final FamyProperties.Jwt config;

    public TokenService(JwtEncoder encoder, ECKey famyJwk, RefreshTokenRepository refreshTokens,
            AppUserRepository users, ObjectProvider<AccessTokenClaimsProvider> claimsProvider, AuditService audit,
            FamyProperties props) {
        this.encoder = encoder;
        this.jwk = famyJwk;
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.claimsProvider = claimsProvider;
        this.audit = audit;
        this.config = props.jwt();
    }

    @Transactional
    public TokenPair issue(AppUser user, String deviceName) {
        return issue(user, deviceName, Ids.newId()).pair();
    }

    /**
     * Rota el refresh token. Se ejecuta en su propia transacción para que la revocación por reutilización
     * se confirme aunque la petición termine en 401.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public TokenPair refresh(String rawRefreshToken) {
        RefreshToken current = find(rawRefreshToken).orElseThrow(TokenService::invalidRefresh);
        Instant now = Instant.now();
        if (current.isRevoked()) {
            if (current.wasRotated()) {
                refreshTokens.revokeChain(current.getFamilyChainId(), now);
                audit.record("auth.refresh_reuse_detected", current.getUserId(), null,
                        Map.of("chain", current.getFamilyChainId().toString()));
            }
            throw invalidRefresh();
        }
        if (current.isExpired(now)) {
            throw invalidRefresh();
        }
        AppUser user = users.findById(current.getUserId()).filter(AppUser::isActive)
                .orElseThrow(TokenService::invalidRefresh);
        Issued issued = issue(user, current.getDeviceName(), current.getFamilyChainId());
        current.rotateTo(issued.refreshTokenId());
        return issued.pair();
    }

    @Transactional
    public void revoke(String rawRefreshToken) {
        find(rawRefreshToken).ifPresent(RefreshToken::revoke);
    }

    @Transactional
    public void revokeAll(UUID userId) {
        refreshTokens.revokeAllForUser(userId, Instant.now());
    }

    @Scheduled(cron = "0 17 3 * * *")
    @Transactional
    public void purgeExpired() {
        refreshTokens.deleteExpiredBefore(Instant.now().minus(7, ChronoUnit.DAYS));
    }

    private Optional<RefreshToken> find(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 200) {
            return Optional.empty();
        }
        return refreshTokens.findByTokenHash(SecureTokens.sha256(raw));
    }

    private record Issued(TokenPair pair, UUID refreshTokenId) {
    }

    private Issued issue(AppUser user, String deviceName, UUID chainId) {
        Instant now = Instant.now();
        Instant exp = now.plus(config.accessTokenTtl());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(config.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(exp)
                .id(Ids.newId().toString());
        AccessTokenClaimsProvider provider = claimsProvider.getIfAvailable();
        if (provider != null) {
            Optional<FamilyClaims> family = provider.activeFamily(user.getId());
            family.ifPresent(f -> claims.claim("fid", f.familyId().toString()).claim("role", f.role()));
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.ES256).keyId(jwk.getKeyID()).build();
        String accessToken = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();

        String rawRefresh = SecureTokens.newToken();
        String device = deviceName == null ? null : deviceName.substring(0, Math.min(deviceName.length(), 100));
        RefreshToken refresh = new RefreshToken(user.getId(), SecureTokens.sha256(rawRefresh), device, chainId,
                now.plus(config.refreshTokenTtl()), ClientInfo.current().ip());
        refreshTokens.save(refresh);
        return new Issued(new TokenPair(accessToken, config.accessTokenTtl().toSeconds(), rawRefresh, user.getId()),
                refresh.getId());
    }

    private static ApiException invalidRefresh() {
        return ApiException.unauthorized("invalid_refresh_token", "La sesión expiró. Inicia sesión de nuevo.");
    }
}
