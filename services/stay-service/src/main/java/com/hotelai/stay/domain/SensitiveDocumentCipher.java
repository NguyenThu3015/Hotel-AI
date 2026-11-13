package com.hotelai.stay.domain;

import com.hotelai.stay.config.StayProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class SensitiveDocumentCipher {
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SensitiveDocumentCipher(StayProperties properties) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(properties.security().documentEncryptionKey());
        } catch (RuntimeException error) {
            throw new IllegalStateException("STAY_DOCUMENT_ENCRYPTION_KEY must be valid Base64", error);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("STAY_DOCUMENT_ENCRYPTION_KEY must decode to 32 bytes");
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    public String encrypt(String documentNumber) {
        if (documentNumber == null || documentNumber.isBlank()) {
            throw new IllegalArgumentException("Document number is required");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(
                    documentNumber.trim().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ciphertext.length)
                            .put(iv)
                            .put(ciphertext)
                            .array());
        } catch (Exception error) {
            throw new IllegalStateException("Cannot encrypt guest document", error);
        }
    }

    public static String last4(String documentNumber) {
        String normalized = documentNumber == null
                ? ""
                : documentNumber.replaceAll("\\s+", "");
        if (normalized.isBlank()) throw new IllegalArgumentException("Document number is required");
        return normalized.substring(Math.max(0, normalized.length() - 4));
    }
}
