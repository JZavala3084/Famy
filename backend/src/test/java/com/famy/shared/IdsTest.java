package com.famy.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IdsTest {

    @Test
    void generatesVersion7UuidsOrderedByTime() throws Exception {
        UUID a = Ids.newId();
        Thread.sleep(2);
        UUID b = Ids.newId();
        assertThat(a.version()).isEqualTo(7);
        assertThat(a.variant()).isEqualTo(2);
        assertThat(a.toString().compareTo(b.toString())).isNegative();
    }

    @Test
    void hashesTokensWithSha256() {
        assertThat(SecureTokens.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(SecureTokens.newToken()).hasSize(43).isNotEqualTo(SecureTokens.newToken());
    }
}
