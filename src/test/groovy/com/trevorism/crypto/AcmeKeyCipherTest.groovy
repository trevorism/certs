package com.trevorism.crypto

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotEquals
import static org.junit.jupiter.api.Assertions.assertThrows

class AcmeKeyCipherTest {

    private static final String PLAINTEXT = "-----BEGIN PRIVATE KEY-----\nabc123\n-----END PRIVATE KEY-----"

    @Test
    void testRoundTrip() {
        AcmeKeyCipher cipher = new AcmeKeyCipher("a-secret-passphrase")
        assertEquals(PLAINTEXT, cipher.decrypt(cipher.encrypt(PLAINTEXT)))
    }

    @Test
    void testEncryptingTwiceProducesDifferentCiphertext() {
        AcmeKeyCipher cipher = new AcmeKeyCipher("a-secret-passphrase")
        assertNotEquals(cipher.encrypt(PLAINTEXT), cipher.encrypt(PLAINTEXT))
    }

    @Test
    void testCiphertextDoesNotContainThePlaintext() {
        AcmeKeyCipher cipher = new AcmeKeyCipher("a-secret-passphrase")
        assertEquals(false, cipher.encrypt(PLAINTEXT).contains("BEGIN PRIVATE KEY"))
    }

    @Test
    void testAnotherPassphraseCannotDecrypt() {
        String ciphertext = new AcmeKeyCipher("a-secret-passphrase").encrypt(PLAINTEXT)
        AcmeKeyCipher wrong = new AcmeKeyCipher("a-different-passphrase")
        assertThrows(Exception) { wrong.decrypt(ciphertext) }
    }

    @Test
    void testBlankPassphraseIsRejected() {
        assertThrows(IllegalStateException) { new AcmeKeyCipher("") }
        assertThrows(IllegalStateException) { new AcmeKeyCipher(null) }
        assertThrows(IllegalStateException) { new AcmeKeyCipher("   ") }
    }
}
