package com.famy.identity.infrastructure.google;

import java.util.Optional;

/** Valida un id_token de Google (firma, iss, aud, exp) y devuelve sus datos. */
public interface GoogleIdTokenVerifier {

    record GoogleUser(String subject, String email, boolean emailVerified, String name, String picture) {
    }

    /** Vacío si el token no es válido. */
    Optional<GoogleUser> verify(String idToken);

    boolean isConfigured();
}
