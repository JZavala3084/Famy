package com.famy.realtime.infrastructure;

import com.famy.family.FamilyMembership;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Seguridad de los frames STOMP entrantes:
 * <ul>
 *   <li>CONNECT exige {@code Authorization: Bearer <access token>} válido.</li>
 *   <li>SUBSCRIBE solo a {@code /topic/families/{id}} y solo si el usuario es miembro activo (se consulta la base).</li>
 *   <li>SEND no está permitido: los cambios se hacen por la API REST.</li>
 * </ul>
 */
class StompAuthInterceptor implements ChannelInterceptor {

    private static final Pattern FAMILY_TOPIC = Pattern.compile(
            "^/topic/families/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$");

    private final JwtDecoder jwtDecoder;
    private final FamilyMembership membership;
    private final WebSocketSessions sessions;

    StompAuthInterceptor(JwtDecoder jwtDecoder, FamilyMembership membership, WebSocketSessions sessions) {
        this.jwtDecoder = jwtDecoder;
        this.membership = membership;
        this.sessions = sessions;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        switch (command) {
            case CONNECT, STOMP -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscription(accessor);
            case SEND -> throw new MessageDeliveryException("SEND no está permitido; usa la API REST");
            default -> {
                // UNSUBSCRIBE, DISCONNECT, ACK...
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null) {
            header = accessor.getFirstNativeHeader("authorization");
        }
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new MessageDeliveryException("Falta el access token");
        }
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(header.substring(7).strip());
        } catch (JwtException e) {
            throw new MessageDeliveryException("Access token inválido o vencido");
        }
        accessor.setUser(new JwtAuthenticationToken(jwt));
        sessions.authenticated(accessor.getSessionId(), UUID.fromString(jwt.getSubject()), jwt.getExpiresAt());
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof JwtAuthenticationToken auth)) {
            throw new MessageDeliveryException("No autenticado");
        }
        String destination = accessor.getDestination();
        Matcher m = destination == null ? null : FAMILY_TOPIC.matcher(destination);
        if (m == null || !m.matches()) {
            throw new MessageDeliveryException("Destino no permitido");
        }
        UUID familyId = UUID.fromString(m.group(1));
        UUID userId = UUID.fromString(auth.getToken().getSubject());
        if (membership.roleOf(familyId, userId).isEmpty()) {
            throw new MessageDeliveryException("No perteneces a esa familia");
        }
    }
}
