/**
 * Módulo realtime: guarda cada {@link com.famy.shared.events.DomainChange} en {@code change_log} y lo envía por
 * WebSocket (STOMP) a {@code /topic/families/{familyId}} tras el commit. Endpoint {@code /ws}; el token va en la
 * cabecera {@code Authorization} del frame CONNECT.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Realtime")
package com.famy.realtime;
