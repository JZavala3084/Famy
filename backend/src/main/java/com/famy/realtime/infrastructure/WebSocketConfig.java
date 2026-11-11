package com.famy.realtime.infrastructure;

import com.famy.family.FamilyMembership;
import com.famy.shared.config.FamyProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;

/**
 * STOMP sobre WebSocket en {@code /ws} con broker simple en memoria (una sola instancia; ver docs/04). Si algún día
 * hay varias instancias, se cambia por un relay a RabbitMQ.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final FamyProperties props;
    private final JwtDecoder jwtDecoder;
    private final FamilyMembership membership;
    private final WebSocketSessions sessions;
    private final TaskScheduler brokerScheduler;

    WebSocketConfig(FamyProperties props, JwtDecoder jwtDecoder, FamilyMembership membership,
            WebSocketSessions sessions, @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler brokerScheduler) {
        this.props = props;
        this.jwtDecoder = jwtDecoder;
        this.membership = membership;
        this.sessions = sessions;
        this.brokerScheduler = brokerScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(props.cors().allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[] {20_000, 20_000})
                .setTaskScheduler(brokerScheduler);
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new StompAuthInterceptor(jwtDecoder, membership, sessions));
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(16 * 1024)
                .addDecoratorFactory(handler -> new TrackingHandler(handler, sessions));
    }

    private static final class TrackingHandler extends WebSocketHandlerDecorator {

        private final WebSocketSessions sessions;

        TrackingHandler(WebSocketHandler delegate, WebSocketSessions sessions) {
            super(delegate);
            this.sessions = sessions;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession session) throws Exception {
            sessions.opened(session);
            super.afterConnectionEstablished(session);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
            sessions.closed(session.getId());
            super.afterConnectionClosed(session, closeStatus);
        }
    }
}
