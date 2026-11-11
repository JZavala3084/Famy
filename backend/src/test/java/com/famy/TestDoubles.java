package com.famy;

import com.famy.identity.infrastructure.google.GoogleIdTokenVerifier;
import com.famy.identity.infrastructure.mail.EmailSender;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Dobles de prueba: buzón en memoria y verificador de Google falso. */
@TestConfiguration(proxyBeanMethods = false)
public class TestDoubles {

    public record SentEmail(String to, String subject, String body) {
    }

    public static class Outbox implements EmailSender {

        private final List<SentEmail> sent = new CopyOnWriteArrayList<>();

        @Override
        public void send(String to, String subject, String body) {
            sent.add(new SentEmail(to, subject, body));
        }

        public List<SentEmail> to(String email) {
            return sent.stream().filter(m -> m.to().equalsIgnoreCase(email)).toList();
        }

        /** Token del último enlace enviado a ese correo cuyo path contiene {@code pathPart}. */
        public String lastToken(String email, String pathPart) {
            Pattern p = Pattern.compile(Pattern.quote(pathPart) + "\\?token=([A-Za-z0-9_\\-%]+)");
            List<SentEmail> mails = to(email);
            for (int i = mails.size() - 1; i >= 0; i--) {
                Matcher m = p.matcher(mails.get(i).body());
                if (m.find()) {
                    return m.group(1);
                }
            }
            throw new AssertionError("No hay correo con " + pathPart + " para " + email);
        }
    }

    /** Acepta id_tokens falsos con formato {@code google|sub|email|verified|name}. */
    public static class FakeGoogle implements GoogleIdTokenVerifier {

        @Override
        public Optional<GoogleUser> verify(String idToken) {
            String[] parts = idToken.split("\\|");
            if (parts.length != 5 || !parts[0].equals("google")) {
                return Optional.empty();
            }
            return Optional.of(new GoogleUser(parts[1], parts[2], Boolean.parseBoolean(parts[3]), parts[4], null));
        }

        @Override
        public boolean isConfigured() {
            return true;
        }
    }

    @Bean
    @Primary
    Outbox outbox() {
        return new Outbox();
    }

    @Bean
    @Primary
    FakeGoogle fakeGoogle() {
        return new FakeGoogle();
    }
}
