package com.famy.identity.api;

import com.famy.identity.application.ProfileService;
import com.famy.identity.application.ProfileService.Profile;
import com.famy.identity.application.ProfileService.ProfileUpdate;
import com.famy.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Perfil")
public class MeController {

    private final ProfileService profiles;

    public MeController(ProfileService profiles) {
        this.profiles = profiles;
    }

    public record ProfileResponse(UUID id, String email, boolean emailVerified, String displayName, String avatarUrl,
            String color, LocalDate birthday, String timezone, String locale, String currency, boolean hasPassword,
            List<String> linkedProviders) {

        static ProfileResponse of(Profile p) {
            var u = p.user();
            return new ProfileResponse(u.getId(), u.getEmail(), u.isEmailVerified(), u.getDisplayName(),
                    u.getAvatarUrl(), u.getColor(), u.getBirthday(), u.getTimezone(), u.getLocale(), u.getCurrency(),
                    u.hasPassword(), p.linkedProviders());
        }
    }

    public record UpdateProfileRequest(
            @Size(min = 1, max = 80) String displayName,
            @Size(max = 500) @Pattern(regexp = "^(https://.*)?$", message = "debe ser una URL https") String avatarUrl,
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "debe ser un color #RRGGBB") String color,
            LocalDate birthday,
            @Size(max = 64) String timezone,
            @Size(max = 16) String locale,
            @Pattern(regexp = "^[A-Za-z]{3}$", message = "debe ser un código ISO 4217") String currency) {
    }

    @GetMapping
    @Operation(summary = "Mi perfil y configuración")
    public ProfileResponse me() {
        return ProfileResponse.of(profiles.get(CurrentUser.id()));
    }

    @PatchMapping
    @Operation(summary = "Actualizar mi perfil y configuración (moneda USD por defecto)")
    public ProfileResponse update(@Valid @RequestBody UpdateProfileRequest req) {
        return ProfileResponse.of(profiles.update(CurrentUser.id(), new ProfileUpdate(req.displayName(),
                req.avatarUrl(), req.color(), req.birthday(), req.timezone(), req.locale(), req.currency())));
    }
}
