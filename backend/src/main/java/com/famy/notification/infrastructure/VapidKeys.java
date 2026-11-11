package com.famy.notification.infrastructure;

import com.famy.shared.config.FamyProperties;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Claves VAPID. Se leen de {@code famy.push.vapid-private-key-file} (por defecto {@code FAMY_DATA_DIR/vapid-private.pem});
 * si el archivo no existe se genera y se guarda, porque cambiar la clave invalida todas las suscripciones de los
 * navegadores.
 */
@Component
public class VapidKeys {

    private static final Logger log = LoggerFactory.getLogger(VapidKeys.class);

    private final KeyPair keys;
    private final String publicKey;

    public VapidKeys(FamyProperties props) {
        String file = props.push().vapidPrivateKeyFile();
        Path path = file == null || file.isBlank() ? Path.of(props.dataDir(), "vapid-private.pem") : Path.of(file);
        this.keys = loadOrCreate(path.toAbsolutePath());
        this.publicKey = WebPushCrypto.b64(WebPushCrypto.encodePoint((ECPublicKey) keys.getPublic()));
    }

    public KeyPair keyPair() {
        return keys;
    }

    /** Clave pública en base64url (la usa el navegador como {@code applicationServerKey}). */
    public String publicKey() {
        return publicKey;
    }

    private static KeyPair loadOrCreate(Path path) {
        try {
            if (Files.exists(path)) {
                return load(Files.readString(path));
            }
            KeyPair kp = WebPushCrypto.generateKeyPair();
            Files.createDirectories(path.getParent());
            String pem = "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                            .encodeToString(kp.getPrivate().getEncoded())
                    + "\n-----END PRIVATE KEY-----\n";
            Files.writeString(path, pem, StandardOpenOption.CREATE_NEW);
            try {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Windows
            }
            log.info("Claves VAPID generadas en {}", path);
            return kp;
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo leer ni crear la clave VAPID en " + path, e);
        }
    }

    static KeyPair load(String pem) throws GeneralSecurityException {
        String base64 = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        KeyFactory kf = KeyFactory.getInstance("EC");
        ECPrivateKey priv = (ECPrivateKey) kf.generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
        ECNamedCurveParameterSpec curve = ECNamedCurveTable.getParameterSpec("secp256r1");
        org.bouncycastle.math.ec.ECPoint q = curve.getG().multiply(priv.getS()).normalize();
        ECPoint w = new ECPoint(new BigInteger(1, q.getAffineXCoord().getEncoded()),
                new BigInteger(1, q.getAffineYCoord().getEncoded()));
        return new KeyPair(kf.generatePublic(new ECPublicKeySpec(w, priv.getParams())), priv);
    }
}
