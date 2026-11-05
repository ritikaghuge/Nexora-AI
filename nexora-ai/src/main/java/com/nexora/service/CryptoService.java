package com.nexora.service;

import com.nexora.config.AppProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** AES-256-GCM encryption for secrets stored in the database (provider API keys). */
@Service
public class CryptoService {
    private static final Logger log = LoggerFactory.getLogger(CryptoService.class);
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(AppProperties props) {
        String secret = props.encryptionKey();
        if (secret == null || secret.isBlank()) secret = loadOrCreateLocalSecret();
        try {
            byte[] k = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(k, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private String loadOrCreateLocalSecret() {
        Path p = Path.of(".nexora-secret");
        try {
            if (Files.exists(p)) return Files.readString(p).trim();
            byte[] b = new byte[32];
            random.nextBytes(b);
            String s = Base64.getEncoder().encodeToString(b);
            Files.writeString(p, s);
            log.warn("APP_ENCRYPTION_KEY not set: generated a local key in ./.nexora-secret. Set APP_ENCRYPTION_KEY in .env for production.");
            return s;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create encryption key file. Set APP_ENCRYPTION_KEY in .env", e);
        }
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(ct, 0, all, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(all);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String enc) {
        try {
            byte[] all = Base64.getDecoder().decode(enc);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot decrypt stored secret (was APP_ENCRYPTION_KEY changed?)", e);
        }
    }
}
