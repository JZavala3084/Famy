package com.famy.shared.ratelimit;

import com.famy.shared.ratelimit.RateLimiter.Policy;
import com.famy.shared.security.CurrentUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rate limiting por IP en los endpoints sensibles (login, registro, recuperación) y por usuario al usar
 * invitaciones. Se registra dentro de la cadena de Spring Security, después de autenticar el bearer token.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private record Rule(HttpMethod method, String pathPrefix, Policy policy, boolean perUser) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(HttpMethod.POST, "/api/v1/auth/login", new Policy("ip-login", 10, Duration.ofMinutes(1)), false),
            new Rule(HttpMethod.POST, "/api/v1/auth/google", new Policy("ip-google", 10, Duration.ofMinutes(1)), false),
            new Rule(HttpMethod.POST, "/api/v1/auth/register", new Policy("ip-register", 10, Duration.ofHours(1)), false),
            new Rule(HttpMethod.POST, "/api/v1/auth/refresh", new Policy("ip-refresh", 30, Duration.ofMinutes(1)), false),
            new Rule(HttpMethod.POST, "/api/v1/auth/password/", new Policy("ip-password", 5, Duration.ofMinutes(15)), false),
            new Rule(HttpMethod.POST, "/api/v1/auth/resend-verification", new Policy("ip-resend", 5, Duration.ofMinutes(15)), false),
            new Rule(HttpMethod.POST, "/api/v1/auth/verify-email", new Policy("ip-verify", 10, Duration.ofMinutes(15)), false),
            new Rule(null, "/api/v1/invitations/", new Policy("user-invitations", 30, Duration.ofMinutes(15)), true));

    private final RateLimiter limiter;

    public RateLimitFilter(RateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        for (Rule rule : RULES) {
            if ((rule.method() == null || rule.method().matches(request.getMethod()))
                    && path.startsWith(rule.pathPrefix())) {
                UUID userId = rule.perUser() ? CurrentUser.idOrNull() : null;
                String key = userId != null ? "u:" + userId : "ip:" + request.getRemoteAddr();
                if (!limiter.tryConsume(rule.policy(), key)) {
                    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                    response.setHeader("Retry-After", String.valueOf(rule.policy().period().toSeconds()));
                    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write("""
                            {"type":"about:blank","title":"Too Many Requests","status":429,\
                            "detail":"Demasiados intentos. Espera unos minutos y vuelve a intentarlo.","code":"rate_limited"}""");
                    return;
                }
                break;
            }
        }
        chain.doFilter(request, response);
    }
}
