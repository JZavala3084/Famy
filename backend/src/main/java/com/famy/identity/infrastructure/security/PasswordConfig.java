package com.famy.identity.infrastructure.security;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
class PasswordConfig {

    /**
     * Argon2id (64 MB, 3 iteraciones, paralelismo 1) dentro de un {@link DelegatingPasswordEncoder}
     * para poder migrar de algoritmo en el futuro (el hash se guarda con prefijo {@code {argon2}}).
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(16, 32, 1, 65536, 3);
        return new DelegatingPasswordEncoder("argon2", Map.of("argon2", argon2));
    }
}
