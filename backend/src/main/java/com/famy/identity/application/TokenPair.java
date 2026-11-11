package com.famy.identity.application;

import java.util.UUID;

/** Resultado de un login o refresh: access token JWT corto + refresh token opaco. */
public record TokenPair(String accessToken, long expiresInSeconds, String refreshToken, UUID userId) {
}
