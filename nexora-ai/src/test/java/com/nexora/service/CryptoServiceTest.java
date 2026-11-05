package com.nexora.service;

import static org.junit.jupiter.api.Assertions.*;

import com.nexora.config.AppProperties;
import org.junit.jupiter.api.Test;

class CryptoServiceTest {
    private final CryptoService crypto = new CryptoService(new AppProperties("unit-test-secret", "./x", null, null));

    @Test
    void roundTrip() {
        String enc = crypto.encrypt("sk-12345");
        assertNotEquals("sk-12345", enc);
        assertFalse(enc.contains("sk-12345"));
        assertEquals("sk-12345", crypto.decrypt(enc));
    }

    @Test
    void sameInputGivesDifferentCiphertext() {
        assertNotEquals(crypto.encrypt("same"), crypto.encrypt("same"));
    }

    @Test
    void tamperedCiphertextIsRejected() {
        String enc = crypto.encrypt("secret");
        String bad = enc.substring(0, enc.length() - 4) + (enc.endsWith("AAAA") ? "BBBB" : "AAAA");
        assertThrows(IllegalStateException.class, () -> crypto.decrypt(bad));
    }
}
