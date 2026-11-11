package com.famy.identity.infrastructure.security;

import com.famy.shared.config.FamyProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Bean
    ECKey famyJwk(FamyProperties props) throws JOSEException {
        FamyProperties.Jwt jwt = props.jwt();
        KeyPair pair;
        if (jwt.privateKeyFile() != null && !jwt.privateKeyFile().isBlank()) {
            pair = JwtKeys.load(Path.of(jwt.privateKeyFile()));
        } else if (jwt.requireKey()) {
            throw new IllegalStateException("Falta JWT_PRIVATE_KEY_FILE (famy.jwt.private-key-file)");
        } else {
            log.warn("Sin JWT_PRIVATE_KEY_FILE: se genera una clave temporal. Las sesiones se pierden al reiniciar. "
                    + "Solo para desarrollo.");
            pair = JwtKeys.generate();
        }
        ECKey key = new ECKey.Builder(Curve.P_256, (ECPublicKey) pair.getPublic())
                .privateKey((ECPrivateKey) pair.getPrivate())
                .build();
        return new ECKey.Builder(key).keyID(key.computeThumbprint().toString()).build();
    }

    @Bean
    JwtEncoder jwtEncoder(ECKey famyJwk) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(famyJwk)));
    }

    @Bean
    JwtDecoder jwtDecoder(ECKey famyJwk, FamyProperties props) {
        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.ES256,
                new ImmutableJWKSet<>(new JWKSet(famyJwk.toPublicJWK()))));
        // Las validaciones de claims (exp, iss) las hace Spring con el validador de abajo.
        processor.setJWTClaimsSetVerifier((claims, context) -> { });
        NimbusJwtDecoder decoder = new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.jwt().issuer()));
        return decoder;
    }
}
