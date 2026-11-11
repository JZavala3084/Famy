package com.famy.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WebPushCryptoTest {

    /** Descifra como lo haría el navegador (RFC 8291 §3, lado del agente de usuario). */
    private static byte[] decrypt(byte[] body, KeyPair ua, byte[] authSecret) throws Exception {
        ByteBuffer buf = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        buf.get(salt);
        assertThat(buf.getInt()).isEqualTo(4096);
        byte[] asPublic = new byte[buf.get()];
        buf.get(asPublic);
        byte[] ciphertext = new byte[buf.remaining()];
        buf.get(ciphertext);

        byte[] uaPublic = WebPushCrypto.encodePoint((ECPublicKey) ua.getPublic());
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(ua.getPrivate());
        ka.doPhase(WebPushCrypto.decodePoint(asPublic, ((ECPublicKey) ua.getPublic()).getParams()), true);
        byte[] ecdh = ka.generateSecret();
        byte[] keyInfo = WebPushCrypto.concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[] {0},
                uaPublic, asPublic);
        byte[] ikm = WebPushCrypto.hkdf(authSecret, ecdh, keyInfo, 32);
        byte[] cek = WebPushCrypto.hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = WebPushCrypto.hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] plain = c.doFinal(ciphertext);
        assertThat(plain[plain.length - 1]).isEqualTo((byte) 2);
        return Arrays.copyOf(plain, plain.length - 1);
    }

    @Test
    void browserCanDecryptTheMessage() throws Exception {
        KeyPair ua = WebPushCrypto.generateKeyPair();
        byte[] auth = new byte[16];
        new java.security.SecureRandom().nextBytes(auth);
        byte[] payload = "{\"title\":\"Pago: Luz\",\"body\":\"Vence hoy · $850.00\"}".getBytes(StandardCharsets.UTF_8);

        byte[] body = WebPushCrypto.encrypt(payload, WebPushCrypto.encodePoint((ECPublicKey) ua.getPublic()), auth);

        assertThat(decrypt(body, ua, auth)).isEqualTo(payload);
    }

    @Test
    void rfc8291ExampleKeyDerivation() throws Exception {
        // Vector de RFC 8291 §5: auth_secret, ecdh_secret e IKM.
        byte[] auth = WebPushCrypto.unb64("BTBZMqHH6r4Tts7J_aSIgg");
        byte[] ecdh = WebPushCrypto.unb64("kyrL1jIIOHEzg3sM2ZWRHDRB62YACZhhSlknJ672kSs");
        byte[] uaPublic = WebPushCrypto.unb64(
                "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
        byte[] asPublic = WebPushCrypto.unb64(
                "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
        byte[] keyInfo = WebPushCrypto.concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[] {0},
                uaPublic, asPublic);
        assertThat(WebPushCrypto.b64(WebPushCrypto.hkdf(auth, ecdh, keyInfo, 32)))
                .isEqualTo("S4lYMb_L0FxCeq0WhDx813KgSYqU26kOyzWUdsXYyrg");
    }

    @Test
    void vapidJwtIsSignedWithEs256() throws Exception {
        KeyPair kp = WebPushCrypto.generateKeyPair();
        String jwt = WebPushCrypto.vapidJwt("https://fcm.googleapis.com", "mailto:admin@famy.local", 1_900_000_000L,
                kp.getPrivate());
        String[] parts = jwt.split("\\.");
        assertThat(parts).hasSize(3);
        String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"aud\":\"https://fcm.googleapis.com\"").contains("\"exp\":1900000000");
        Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format");
        sig.initVerify(kp.getPublic());
        sig.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(sig.verify(Base64.getUrlDecoder().decode(parts[2]))).isTrue();
    }
}
