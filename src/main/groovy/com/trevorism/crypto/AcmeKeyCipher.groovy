package com.trevorism.crypto

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.security.SecureRandom

class AcmeKeyCipher {

    private static final int IV_LENGTH = 12
    private static final int TAG_BITS = 128
    private static final String TRANSFORMATION = "AES/GCM/NoPadding"

    private final SecretKeySpec secretKey
    private final SecureRandom random = new SecureRandom()

    AcmeKeyCipher(String passphrase) {
        if (!passphrase?.trim()) {
            throw new IllegalStateException("An encryptionKey property is required to store the acme account key")
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(passphrase.getBytes("UTF-8"))
        this.secretKey = new SecretKeySpec(digest, "AES")
    }

    String encrypt(String plaintext) {
        byte[] iv = new byte[IV_LENGTH]
        random.nextBytes(iv)
        Cipher cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv))
        byte[] encrypted = cipher.doFinal(plaintext.getBytes("UTF-8"))
        byte[] combined = new byte[iv.length + encrypted.length]
        System.arraycopy(iv, 0, combined, 0, iv.length)
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length)
        return Base64.getEncoder().encodeToString(combined)
    }

    String decrypt(String ciphertext) {
        byte[] combined = Base64.getDecoder().decode(ciphertext)
        byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH)
        byte[] encrypted = Arrays.copyOfRange(combined, IV_LENGTH, combined.length)
        Cipher cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv))
        return new String(cipher.doFinal(encrypted), "UTF-8")
    }
}
