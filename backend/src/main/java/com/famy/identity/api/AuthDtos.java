package com.famy.identity.api;

import com.famy.identity.application.PasswordPolicy;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** DTOs de {@link AuthController}. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH) String password,
            @NotBlank @Size(max = 80) String displayName,
            @Size(max = 64) @Schema(example = "America/Mexico_City") String timezone,
            @Size(max = 16) @Schema(example = "es") String locale) {
    }

    public record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    public record TokenRequest(@NotBlank @Size(max = 200) String token) {
    }

    public record LoginRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = PasswordPolicy.MAX_LENGTH) String password,
            @Size(max = 100) @Schema(description = "Nombre del dispositivo, p. ej. «Pixel de Ana»") String deviceName) {
    }

    public record GoogleLoginRequest(@NotBlank @Size(max = 4096) String idToken, @Size(max = 100) String deviceName) {
    }

    /** En web el refresh token viaja en cookie; en móvil, en el cuerpo. */
    public record RefreshRequest(@Size(max = 200) String refreshToken) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH) String newPassword) {
    }

    public record ChangePasswordRequest(
            @Size(max = PasswordPolicy.MAX_LENGTH) String currentPassword,
            @NotBlank @Size(min = PasswordPolicy.MIN_LENGTH, max = PasswordPolicy.MAX_LENGTH) String newPassword) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TokenResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            @Schema(description = "Solo para clientes móviles; en web va en la cookie HttpOnly famy_rt") String refreshToken) {
    }

    public record MessageResponse(String message) {
    }
}
