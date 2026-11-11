package com.famy.identity.api;

import com.famy.identity.api.AuthDtos.ChangePasswordRequest;
import com.famy.identity.api.AuthDtos.EmailRequest;
import com.famy.identity.api.AuthDtos.GoogleLoginRequest;
import com.famy.identity.api.AuthDtos.LoginRequest;
import com.famy.identity.api.AuthDtos.MessageResponse;
import com.famy.identity.api.AuthDtos.RefreshRequest;
import com.famy.identity.api.AuthDtos.RegisterRequest;
import com.famy.identity.api.AuthDtos.ResetPasswordRequest;
import com.famy.identity.api.AuthDtos.TokenRequest;
import com.famy.identity.api.AuthDtos.TokenResponse;
import com.famy.identity.application.AuthService;
import com.famy.identity.application.AuthService.RegisterCommand;
import com.famy.identity.application.TokenPair;
import com.famy.identity.application.TokenService;
import com.famy.shared.config.FamyProperties;
import com.famy.shared.error.ApiException;
import com.famy.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autenticación. Clientes web envían {@code X-Famy-Client: web}: el refresh token se entrega en una cookie
 * {@code HttpOnly; Secure; SameSite=Strict} limitada a {@code /api/v1/auth} y nunca en el cuerpo.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación")
public class AuthController {

    static final String CLIENT_HEADER = "X-Famy-Client";
    static final String REFRESH_COOKIE = "famy_rt";
    private static final String COOKIE_PATH = "/api/v1/auth";
    private static final String ACCEPTED = "Si los datos son correctos, te enviamos un correo con los siguientes pasos.";

    private final AuthService auth;
    private final TokenService tokens;
    private final FamyProperties props;

    public AuthController(AuthService auth, TokenService tokens, FamyProperties props) {
        this.auth = auth;
        this.tokens = tokens;
        this.props = props;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Registro con correo y contraseña", description = "Siempre responde 202 para no revelar si el correo existe.")
    public MessageResponse register(@Valid @RequestBody RegisterRequest req) {
        auth.register(new RegisterCommand(req.email(), req.password(), req.displayName(), req.timezone(), req.locale()));
        return new MessageResponse("Revisa tu correo para confirmar tu cuenta.");
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Confirmar el correo con el token del enlace")
    public void verifyEmail(@Valid @RequestBody TokenRequest req) {
        auth.verifyEmail(req.token());
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Reenviar el correo de verificación")
    public MessageResponse resendVerification(@Valid @RequestBody EmailRequest req) {
        auth.resendVerification(req.email());
        return new MessageResponse(ACCEPTED);
    }

    @PostMapping("/login")
    @Operation(summary = "Login con correo y contraseña")
    public TokenResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest request,
            HttpServletResponse response) {
        return respond(auth.login(req.email(), req.password(), req.deviceName()), request, response);
    }

    @PostMapping("/google")
    @Operation(summary = "Login o registro con Google", description = "Recibe el id_token obtenido por la web (Google Identity Services) o el móvil (Credential Manager / Google Sign-In).")
    public TokenResponse google(@Valid @RequestBody GoogleLoginRequest req, HttpServletRequest request,
            HttpServletResponse response) {
        return respond(auth.loginWithGoogle(req.idToken(), req.deviceName()), request, response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotar el refresh token y obtener un nuevo access token")
    public TokenResponse refresh(@Valid @RequestBody(required = false) RefreshRequest req, HttpServletRequest request,
            HttpServletResponse response) {
        String raw = refreshTokenFrom(req, request);
        try {
            return respond(tokens.refresh(raw), request, response);
        } catch (ApiException e) {
            clearCookie(response);
            throw e;
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cerrar sesión en este dispositivo")
    public void logout(@Valid @RequestBody(required = false) RefreshRequest req, HttpServletRequest request,
            HttpServletResponse response) {
        String raw = req != null && req.refreshToken() != null ? req.refreshToken() : cookieValue(request);
        tokens.revoke(raw);
        clearCookie(response);
    }

    @PostMapping("/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cerrar sesión en todos los dispositivos")
    public void logoutAll(HttpServletResponse response) {
        tokens.revokeAll(CurrentUser.id());
        clearCookie(response);
    }

    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Pedir el correo para restablecer la contraseña")
    public MessageResponse forgotPassword(@Valid @RequestBody EmailRequest req) {
        auth.forgotPassword(req.email());
        return new MessageResponse(ACCEPTED);
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Restablecer la contraseña con el token del correo")
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        auth.resetPassword(req.token(), req.newPassword());
    }

    @PostMapping("/password/change")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cambiar la contraseña (cierra las demás sesiones)")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req, HttpServletResponse response) {
        auth.changePassword(CurrentUser.id(), req.currentPassword(), req.newPassword());
        clearCookie(response);
    }

    private TokenResponse respond(TokenPair pair, HttpServletRequest request, HttpServletResponse response) {
        if (isWebClient(request)) {
            response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, pair.refreshToken())
                    .httpOnly(true)
                    .secure(props.jwt().secureCookie())
                    .sameSite("Strict")
                    .path(COOKIE_PATH)
                    .maxAge(props.jwt().refreshTokenTtl())
                    .build().toString());
            return new TokenResponse(pair.accessToken(), "Bearer", pair.expiresInSeconds(), null);
        }
        return new TokenResponse(pair.accessToken(), "Bearer", pair.expiresInSeconds(), pair.refreshToken());
    }

    private String refreshTokenFrom(RefreshRequest req, HttpServletRequest request) {
        if (req != null && req.refreshToken() != null && !req.refreshToken().isBlank()) {
            return req.refreshToken();
        }
        // La cookie solo se acepta con la cabecera personalizada (defensa CSRF adicional a SameSite=Strict).
        if (!isWebClient(request)) {
            throw ApiException.unauthorized("invalid_refresh_token", "La sesión expiró. Inicia sesión de nuevo.");
        }
        return cookieValue(request);
    }

    private void clearCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(REFRESH_COOKIE, "")
                .httpOnly(true).secure(props.jwt().secureCookie()).sameSite("Strict").path(COOKIE_PATH)
                .maxAge(Duration.ZERO).build().toString());
    }

    private static boolean isWebClient(HttpServletRequest request) {
        return "web".equalsIgnoreCase(request.getHeader(CLIENT_HEADER));
    }

    private static String cookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies).filter(c -> REFRESH_COOKIE.equals(c.getName())).map(Cookie::getValue)
                .findFirst().orElse(null);
    }
}
