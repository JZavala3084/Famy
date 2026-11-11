package com.famy.identity.application;

import com.famy.shared.error.ApiException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Política de contraseñas (ver docs/03): mínimo 10 caracteres, sin reglas de composición, rechazo de
 * contraseñas comunes o filtradas conocidas (lista local).
 */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 128;

    private final Set<String> common = new HashSet<>();

    public PasswordPolicy() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("security/common-passwords.txt").getInputStream(), StandardCharsets.UTF_8))) {
            reader.lines().map(String::strip).filter(s -> !s.isEmpty() && !s.startsWith("#"))
                    .map(s -> s.toLowerCase(Locale.ROOT)).forEach(common::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void validate(String password, String email) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw ApiException.badRequest("weak_password",
                    "La contraseña debe tener al menos " + MIN_LENGTH + " caracteres.");
        }
        if (password.length() > MAX_LENGTH) {
            throw ApiException.badRequest("weak_password",
                    "La contraseña no puede tener más de " + MAX_LENGTH + " caracteres.");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (common.contains(lower)) {
            throw ApiException.badRequest("weak_password",
                    "Esa contraseña es muy común o aparece en filtraciones conocidas. Elige otra.");
        }
        if (email != null) {
            String local = email.split("@")[0].toLowerCase(Locale.ROOT);
            if (local.length() >= 4 && lower.contains(local)) {
                throw ApiException.badRequest("weak_password", "La contraseña no debe contener tu correo.");
            }
        }
    }
}
