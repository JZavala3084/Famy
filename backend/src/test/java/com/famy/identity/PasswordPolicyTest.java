package com.famy.identity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.famy.identity.application.PasswordPolicy;
import com.famy.shared.error.ApiException;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsLongPassphraseWithoutCompositionRules() {
        assertThatCode(() -> policy.validate("caballo bateria grapa", "ana@example.com")).doesNotThrowAnyException();
    }

    @Test
    void rejectsShortCommonAndEmailBasedPasswords() {
        assertThatThrownBy(() -> policy.validate("corta123", null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> policy.validate("Password123", null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> policy.validate("contraseña123", null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> policy.validate("josezavala2024", "josezavala@example.com"))
                .isInstanceOf(ApiException.class);
    }
}
