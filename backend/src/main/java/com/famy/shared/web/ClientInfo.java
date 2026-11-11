package com.famy.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** IP y User-Agent de la petición actual (la IP real llega vía cabeceras del proxy, ver forward-headers-strategy). */
public record ClientInfo(String ip, String userAgent) {

    private static final ClientInfo NONE = new ClientInfo(null, null);

    public static ClientInfo current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            String ua = req.getHeader("User-Agent");
            if (ua != null && ua.length() > 255) {
                ua = ua.substring(0, 255);
            }
            return new ClientInfo(req.getRemoteAddr(), ua);
        }
        return NONE;
    }
}
