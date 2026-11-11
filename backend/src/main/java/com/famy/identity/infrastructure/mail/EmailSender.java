package com.famy.identity.infrastructure.mail;

/** Envío de correos transaccionales (verificación, recuperación). */
public interface EmailSender {

    void send(String to, String subject, String body);
}
