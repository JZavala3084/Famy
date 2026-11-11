package com.famy.notification.infrastructure;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cifrado de mensajes Web Push (RFC 8291, {@code aes128gcm} de RFC 8188) y firma VAPID (RFC 8292) solo con la JDK.
 */
public final class WebPushCrypto {

    static final int RECORD_SIZE = 4096;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DEC = Base64.getUrlDecoder();

    private WebPushCrypto() {
    }

    /**
     * Cifra {@code payload} para la suscripción del navegador.
     *
     * @param uaPublic  clave pública del navegador ({@code keys.p256dh}, punto sin comprimir de 65 bytes)
     * @param authSecret secreto de autenticación del navegador ({@code keys.auth}, 16 bytes)
     * @return cuerpo listo para enviar con {@code Content-Encoding: aes128gcm}
     */
    public static byte[] encrypt(byte[] payload, byte[] uaPublic, byte[] authSecret) throws GeneralSecurityException {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return encrypt(payload, uaPublic, authSecret, generateKeyPair(), salt);
    }

    static byte[] encrypt(byte[] payload, byte[] uaPublic, byte[] authSecret, KeyPair asKeys, byte[] salt)
            throws GeneralSecurityException {
        if (payload.length > RECORD_SIZE - 17 - 86) {
            throw new IllegalArgumentException("El mensaje push es demasiado grande");
        }
        ECPublicKey asPublicKey = (ECPublicKey) asKeys.getPublic();
        byte[] asPublic = encodePoint(asPublicKey);
        PublicKey ua = decodePoint(uaPublic, asPublicKey.getParams());

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(asKeys.getPrivate());
        ka.doPhase(ua, true);
        byte[] ecdhSecret = ka.generateSecret();

        // IKM = HKDF(auth_secret, ecdh_secret, "WebPush: info" || 0 || ua_public || as_public, 32)
        byte[] keyInfo = concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[] {0}, uaPublic,
                asPublic);
        byte[] ikm = hkdf(authSecret, ecdhSecret, keyInfo, 32);
        byte[] cek = hkdf(salt, ikm, info("Content-Encoding: aes128gcm"), 16);
        byte[] nonce = hkdf(salt, ikm, info("Content-Encoding: nonce"), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        // Un solo registro: contenido + delimitador 0x02 (último registro), sin relleno.
        byte[] ciphertext = cipher.doFinal(concat(payload, new byte[] {2}));

        ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length);
        header.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic);
        return concat(header.array(), ciphertext);
    }

    /** JWT VAPID firmado con ES256: {@code aud} = origen del servicio push, vence en 12 h. */
    public static String vapidJwt(String audience, String subject, long expiresAtEpochSeconds, PrivateKey key)
            throws GeneralSecurityException {
        String header = b64(
                "{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = b64(("{\"aud\":\"" + jsonEscape(audience) + "\",\"exp\":" + expiresAtEpochSeconds
                + ",\"sub\":\"" + jsonEscape(subject) + "\"}").getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + claims;
        Signature sig = Signature.getInstance("SHA256withECDSAinP1363Format");
        sig.initSign(key);
        sig.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + b64(sig.sign());
    }

    public static KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        return gen.generateKeyPair();
    }

    /** Punto sin comprimir (0x04 || X || Y), 65 bytes. */
    public static byte[] encodePoint(ECPublicKey key) {
        byte[] x = fixed(key.getW().getAffineX().toByteArray());
        byte[] y = fixed(key.getW().getAffineY().toByteArray());
        return concat(new byte[] {4}, x, y);
    }

    static PublicKey decodePoint(byte[] point, ECParameterSpec params) throws GeneralSecurityException {
        if (point.length != 65 || point[0] != 4) {
            throw new GeneralSecurityException("Clave p256dh inválida");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(point, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(point, 33, 65));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), params));
    }

    public static String b64(byte[] bytes) {
        return B64URL.encodeToString(bytes);
    }

    public static byte[] unb64(String s) {
        return B64URL_DEC.decode(s.strip().replace('+', '-').replace('/', '_').replace("=", ""));
    }

    static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        byte[] okm = mac.doFinal(concat(info, new byte[] {1}));
        return Arrays.copyOf(okm, length);
    }

    private static byte[] info(String label) {
        return concat(label.getBytes(StandardCharsets.US_ASCII), new byte[] {0});
    }

    private static byte[] fixed(byte[] b) {
        if (b.length == 32) {
            return b;
        }
        byte[] out = new byte[32];
        if (b.length > 32) {
            System.arraycopy(b, b.length - 32, out, 0, 32);
        } else {
            System.arraycopy(b, 0, out, 32 - b.length, b.length);
        }
        return out;
    }

    static byte[] concat(byte[]... parts) {
        int n = 0;
        for (byte[] p : parts) {
            n += p.length;
        }
        byte[] out = new byte[n];
        int i = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, i, p.length);
            i += p.length;
        }
        return out;
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
