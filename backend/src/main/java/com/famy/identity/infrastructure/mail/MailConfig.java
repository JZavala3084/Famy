package com.famy.identity.infrastructure.mail;

import com.famy.shared.config.FamyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
class MailConfig {

    private static final Logger log = LoggerFactory.getLogger(MailConfig.class);

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    EmailSender emailSender(FamyProperties props, ObjectProvider<JavaMailSender> javaMailSender) {
        if ("smtp".equalsIgnoreCase(props.mail().mode())) {
            JavaMailSender sender = javaMailSender.getIfAvailable();
            if (sender == null) {
                throw new IllegalStateException("famy.mail.mode=smtp requiere spring.mail.host (MAIL_HOST)");
            }
            return (to, subject, body) -> {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(props.mail().from());
                msg.setTo(to);
                msg.setSubject(subject);
                msg.setText(body);
                sender.send(msg);
            };
        }
        log.warn("famy.mail.mode=log: los correos se escriben en el log en vez de enviarse. Solo para desarrollo.");
        return (to, subject, body) -> log.info("Correo para {} | {}\n{}", to, subject, body);
    }
}
