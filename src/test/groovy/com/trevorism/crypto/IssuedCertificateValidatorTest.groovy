package com.trevorism.crypto

import com.trevorism.CertificateTestFactory
import com.trevorism.model.IssuedCertificate
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows

class IssuedCertificateValidatorTest {

    @Test
    void testValidCertificatePasses() {
        IssuedCertificate issued = CertificateTestFactory.issued()
        IssuedCertificateValidator.validateForUpload(issued, CertificateTestFactory.WILDCARD)
        assertEquals(CertificateTestFactory.WILDCARD, IssuedCertificateValidator.readDnsNames(issued.leaf).first())
    }

    @Test
    void testStagingIssuerIsRejected() {
        IssuedCertificate issued = CertificateTestFactory.issued(issuerName: CertificateTestFactory.STAGING_CA_NAME)
        CertificateValidationException e = assertThrows(CertificateValidationException) {
            IssuedCertificateValidator.validateForUpload(issued, CertificateTestFactory.WILDCARD)
        }
        assertEquals("issuerIsNotStaging", e.assertionName)
    }

    @Test
    void testChainOfOneIsRejected() {
        IssuedCertificate issued = CertificateTestFactory.issued(chain: [CertificateTestFactory.leafCertificate()])
        CertificateValidationException e = assertThrows(CertificateValidationException) {
            IssuedCertificateValidator.validateForUpload(issued, CertificateTestFactory.WILDCARD)
        }
        assertEquals("chainLength", e.assertionName)
    }

    @Test
    void testUnrelatedChainIsRejected() {
        IssuedCertificate issued = CertificateTestFactory.issued(
                chain: [CertificateTestFactory.leafCertificate(), CertificateTestFactory.leafCertificate()])
        CertificateValidationException e = assertThrows(CertificateValidationException) {
            IssuedCertificateValidator.validateForUpload(issued, CertificateTestFactory.WILDCARD)
        }
        assertEquals("chainSignatures", e.assertionName)
    }

    @Test
    void testExpiredLeafIsRejected() {
        IssuedCertificate issued = CertificateTestFactory.issued(notAfter: CertificateTestFactory.pastDate())
        CertificateValidationException e = assertThrows(CertificateValidationException) {
            IssuedCertificateValidator.validateForUpload(issued, CertificateTestFactory.WILDCARD)
        }
        assertEquals("leafNotExpired", e.assertionName)
    }

    @Test
    void testWrongKeySizeIsRejected() {
        def smallKey = CertificateTestFactory.generate(1024)
        IssuedCertificate issued = CertificateTestFactory.issued(leafKey: smallKey, keyPair: smallKey)
        CertificateValidationException e = assertThrows(CertificateValidationException) {
            IssuedCertificateValidator.validateForUpload(issued, CertificateTestFactory.WILDCARD)
        }
        assertEquals("keySize", e.assertionName)
    }

    @Test
    void testPrivateKeyFromAnotherCertificateIsRejected() {
        IssuedCertificate issued = CertificateTestFactory.issued(keyPair: CertificateTestFactory.OTHER_KEY)
        CertificateValidationException e = assertThrows(CertificateValidationException) {
            IssuedCertificateValidator.validateForUpload(issued, CertificateTestFactory.WILDCARD)
        }
        assertEquals("keyMatchesCertificate", e.assertionName)
    }

    @Test
    void testMissingWildcardNameIsRejected() {
        IssuedCertificate issued = CertificateTestFactory.issued(dnsNames: ["action.trevorism.com"])
        CertificateValidationException e = assertThrows(CertificateValidationException) {
            IssuedCertificateValidator.validateForUpload(issued, CertificateTestFactory.WILDCARD)
        }
        assertEquals("subjectAlternativeName", e.assertionName)
    }

    @Test
    void testWildcardIsMatchedLiterallyAndNotAsAPattern() {
        IssuedCertificate issued = CertificateTestFactory.issued(dnsNames: ["xaction.trevorism.com"])
        CertificateValidationException e = assertThrows(CertificateValidationException) {
            IssuedCertificateValidator.validateForUpload(issued, CertificateTestFactory.WILDCARD)
        }
        assertEquals("subjectAlternativeName", e.assertionName)
    }

    @Test
    void testReadDnsNamesReturnsEveryName() {
        def leaf = CertificateTestFactory.leafCertificate(dnsNames: ["*.action.trevorism.com", "action.trevorism.com"])
        assertEquals(["*.action.trevorism.com", "action.trevorism.com"], IssuedCertificateValidator.readDnsNames(leaf))
    }

    @Test
    void testReadDnsNamesOnCertificateWithoutNames() {
        assertEquals([], IssuedCertificateValidator.readDnsNames(CertificateTestFactory.caCertificate()))
    }
}
