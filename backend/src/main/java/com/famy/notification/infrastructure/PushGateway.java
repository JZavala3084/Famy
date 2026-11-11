package com.famy.notification.infrastructure;

/** Envía un mensaje Web Push ya armado a un servicio de push (FCM, Mozilla, Apple...). */
public interface PushGateway {

    enum Result {
        DELIVERED,
        /** La suscripción ya no existe (404 / 410): hay que borrarla. */
        GONE,
        FAILED
    }

    record Target(String endpoint, String p256dh, String auth) {
    }

    /**
     * @param payload JSON en claro (se cifra aquí)
     * @param ttlSeconds cuánto puede esperar el servicio de push si el dispositivo está desconectado
     * @param topic   reemplaza un mensaje pendiente con el mismo tema (máx. 32 caracteres base64url), o nulo
     */
    Result send(Target target, byte[] payload, int ttlSeconds, String topic);
}
