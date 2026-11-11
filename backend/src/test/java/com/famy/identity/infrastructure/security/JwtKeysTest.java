package com.famy.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JwtKeysTest {

    @Test
    void loadsPkcs8PemAndDerivesTheSamePublicKey(@TempDir Path dir) throws Exception {
        KeyPair original = JwtKeys.generate();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(original.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        Path file = dir.resolve("jwt.pem");
        Files.writeString(file, pem);

        KeyPair loaded = JwtKeys.load(file);
        assertThat(loaded.getPublic().getEncoded()).isEqualTo(original.getPublic().getEncoded());
    }
}
