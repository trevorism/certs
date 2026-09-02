package com.trevorism.service

import com.trevorism.PropertiesProvider
import com.trevorism.model.AcmeAccountRecord
import com.trevorism.model.RotationRequest
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class Acme4jCertificateIssuerTest {

    @Test
    void testProductionDirectoryIsTheDefault() {
        assertEquals(Acme4jCertificateIssuer.PRODUCTION_DIRECTORY, Acme4jCertificateIssuer.directoryFor(RotationRequest.PRODUCTION))
        assertEquals(Acme4jCertificateIssuer.PRODUCTION_DIRECTORY, Acme4jCertificateIssuer.directoryFor(null))
        assertEquals(Acme4jCertificateIssuer.PRODUCTION_DIRECTORY, Acme4jCertificateIssuer.directoryFor("anything-else"))
    }

    @Test
    void testStagingDirectoryIsSelectedCaseInsensitively() {
        assertEquals(Acme4jCertificateIssuer.STAGING_DIRECTORY, Acme4jCertificateIssuer.directoryFor("staging"))
        assertEquals(Acme4jCertificateIssuer.STAGING_DIRECTORY, Acme4jCertificateIssuer.directoryFor("STAGING"))
    }

    @Test
    void testTrailingDotIsStrippedFromTheAcmeRecordName() {
        assertEquals("_acme-challenge.action.trevorism.com",
                Acme4jCertificateIssuer.stripTrailingDot("_acme-challenge.action.trevorism.com."))
    }

    @Test
    void testARecordNameWithoutATrailingDotIsUnchanged() {
        assertEquals("_acme-challenge.action.trevorism.com",
                Acme4jCertificateIssuer.stripTrailingDot("_acme-challenge.action.trevorism.com"))
    }

    @Test
    void testAMissingEncryptionKeyFailsBeforeTheAccountStoreIsTouched() {
        boolean storeTouched = false
        boolean challengePublished = false

        Acme4jCertificateIssuer issuer = new Acme4jCertificateIssuer()
        issuer.propertiesProvider = [getProperty: { String name -> null }] as PropertiesProvider
        issuer.acmeAccountStore = [
                load : { String server -> storeTouched = true; return null },
                store: { AcmeAccountRecord record -> storeTouched = true; return record }
        ] as AcmeAccountStore

        Dns01ChallengeHandler handler = [
                publish: { String name, String digest -> challengePublished = true },
                cleanup: { String name -> }
        ] as Dns01ChallengeHandler

        IllegalStateException e = assertThrows(IllegalStateException) {
            issuer.issue("*.draw.trevorism.com", RotationRequest.STAGING, handler)
        }

        assertTrue(e.message.contains("encryptionKey"))
        assertFalse(storeTouched, "the acme account store must not be reached without a usable cipher")
        assertFalse(challengePublished, "no dns challenge should be published without a usable cipher")
    }
}
