package com.famy.shared.files;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FileStorageTest {

    @Test
    void detectsTypeFromContentNotName() {
        assertThat(FileStorage.sniff("%PDF-1.7\n".getBytes())).contains("application/pdf");
        assertThat(FileStorage.sniff(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).contains("image/jpeg");
        assertThat(FileStorage.sniff("<svg onload=alert(1)>".getBytes())).isEmpty();
        assertThat(FileStorage.sniff("<html>".getBytes())).isEmpty();
    }
}
