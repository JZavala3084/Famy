package com.famy.identity.application;

import com.famy.identity.UserDirectory;
import com.famy.identity.UserSummary;
import com.famy.identity.domain.AppUser;
import com.famy.identity.domain.UserIdentity;
import com.famy.identity.infrastructure.AppUserRepository;
import com.famy.identity.infrastructure.UserIdentityRepository;
import com.famy.shared.error.ApiException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Perfil y configuración del usuario (RF-1.5, RF-1.7). También implementa {@link UserDirectory}. */
@Service
public class ProfileService implements UserDirectory {

    private final AppUserRepository users;
    private final UserIdentityRepository identities;

    public ProfileService(AppUserRepository users, UserIdentityRepository identities) {
        this.users = users;
        this.identities = identities;
    }

    public record Profile(AppUser user, List<String> linkedProviders) {
    }

    public record ProfileUpdate(String displayName, String avatarUrl, String color, LocalDate birthday,
            String timezone, String locale, String currency) {
    }

    @Transactional(readOnly = true)
    public Profile get(UUID userId) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("user_not_found", "Usuario no encontrado."));
        List<String> providers = identities.findByUserId(userId).stream().map(UserIdentity::getProvider).toList();
        return new Profile(user, providers);
    }

    @Transactional
    public Profile update(UUID userId, ProfileUpdate update) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> ApiException.notFound("user_not_found", "Usuario no encontrado."));
        String currency = null;
        if (update.currency() != null) {
            try {
                currency = Currency.getInstance(update.currency().toUpperCase()).getCurrencyCode();
            } catch (IllegalArgumentException e) {
                throw ApiException.badRequest("invalid_currency", "Moneda no válida (usa un código ISO 4217).");
            }
        }
        String timezone = update.timezone() == null ? null : AuthService.validTimezone(update.timezone());
        user.updateProfile(update.displayName(), update.avatarUrl(), update.color(), update.birthday(), timezone,
                update.locale(), currency);
        return get(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSummary> find(UUID userId) {
        return users.findById(userId).map(AppUser::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UserSummary> findAll(Collection<UUID> userIds) {
        return users.findAllById(userIds).stream().map(AppUser::toSummary)
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
    }
}
