package com.trevorism.crypto

import com.trevorism.CertificateTestFactory
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.junit.jupiter.api.Test

import java.security.KeyPair
import java.security.interfaces.RSAPrivateKey

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue

class PrivateKeyConverterTest {

    @Test
    void testConvertedKeyUsesTheTraditionalPkcs1Header() {
        String pem = PrivateKeyConverter.toPkcs1Pem(CertificateTestFactory.LEAF_KEY.getPrivate())
        assertTrue(pem.startsWith(PrivateKeyConverter.PKCS1_HEADER))
    }

    @Test
    void testJdkKeysAreEncodedAsPkcs8BeforeConversion() {
        assertEquals("PKCS#8", CertificateTestFactory.LEAF_KEY.getPrivate().getFormat())
    }

    @Test
    void testConversionPreservesTheKey() {
        RSAPrivateKey original = (RSAPrivateKey) CertificateTestFactory.LEAF_KEY.getPrivate()
        String pem = PrivateKeyConverter.toPkcs1Pem(original)

        PEMParser parser = new PEMParser(new StringReader(pem))
        KeyPair parsed = new JcaPEMKeyConverter().getKeyPair((PEMKeyPair) parser.readObject())
        parser.close()

        assertEquals(original.getModulus(), ((RSAPrivateKey) parsed.getPrivate()).getModulus())
        assertEquals(original.getPrivateExponent(), ((RSAPrivateKey) parsed.getPrivate()).getPrivateExponent())
    }
}
