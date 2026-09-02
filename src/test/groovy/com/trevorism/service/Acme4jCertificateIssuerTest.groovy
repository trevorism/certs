package com.trevorism.service

import com.trevorism.model.RotationRequest
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

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
}
