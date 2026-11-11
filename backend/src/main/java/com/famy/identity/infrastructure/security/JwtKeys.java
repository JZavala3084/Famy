package com.famy.identity.infrastructure.security;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;

/** Carga o genera el par de claves EC P-256 que firma los access tokens (ES256). */
final class JwtKeys {

    private JwtKeys() {
    }

    /**
     * Lee una clave privada PEM PKCS#8 ({@code -----BEGIN PRIVATE KEY-----}), por ejemplo generada con
     * {@code openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out jwt-private.pem},
     * y deriva la clave pública.
     */
    static KeyPair load(Path pemFile) {
        try {
            String pem = Files.readString(pemFile);
            if (!pem.contains("BEGIN PRIVATE KEY")) {
                throw new IllegalStateException("La clave JWT debe estar en formato PEM PKCS#8 (BEGIN PRIVATE KEY)");
            }
            String base64 = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
            KeyFactory kf = KeyFactory.getInstance("EC");
            ECPrivateKey priv = (ECPrivateKey) kf.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
            return new KeyPair(derivePublic(priv, kf), priv);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo leer la clave JWT de " + pemFile, e);
        }
    }

    static KeyPair generate() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
            gen.initialize(new ECGenParameterSpec("secp256r1"));
            return gen.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ECPublicKey derivePublic(ECPrivateKey priv, KeyFactory kf) throws GeneralSecurityException {
        ECNamedCurveParameterSpec curve = ECNamedCurveTable.getParameterSpec("secp256r1");
        org.bouncycastle.math.ec.ECPoint q = curve.getG().multiply(priv.getS()).normalize();
        ECPoint w = new ECPoint(new BigInteger(1, q.getAffineXCoord().getEncoded()),
                new BigInteger(1, q.getAffineYCoord().getEncoded()));
        return (ECPublicKey) kf.generatePublic(new ECPublicKeySpec(w, priv.getParams()));
    }
}
