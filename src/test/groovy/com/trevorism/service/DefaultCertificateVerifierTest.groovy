package com.trevorism.service

import com.trevorism.model.CertificateVerification
import com.trevorism.model.ManagedCertificate
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assertions.assertTrue

class DefaultCertificateVerifierTest {

    private static final String SERIAL = "6d9820944fa2d75ffa1b574d1b1ca32ec49"

    private static ManagedCertificate certificate(String serial = SERIAL) {
        return new ManagedCertificate(id: "cert-1", category: "draw", wildcard: "*.draw.trevorism.com",
                gcpProject: "trevorism-draw", probeHost: "timeline.draw.trevorism.com", serial: serial)
    }

    private static DefaultCertificateVerifier verifierSeeing(Closure reader) {
        DefaultCertificateVerifier verifier = new DefaultCertificateVerifier()
        verifier.edgeCertificateInspector = [readSerial: reader] as EdgeCertificateInspector
        return verifier
    }

    @Test
    void testMatchingSerialVerifies() {
        CertificateVerification result = verifierSeeing { String host -> SERIAL }.verify(certificate())
        assertTrue(result.matches)
        assertEquals("timeline.draw.trevorism.com", result.probeHost)
    }

    @Test
    void testOpensslPaddedSerialStillVerifies() {
        CertificateVerification result =
                verifierSeeing { String host -> "06D9820944FA2D75FFA1B574D1B1CA32EC49" }.verify(certificate())
        assertTrue(result.matches, "a leading zero from openssl must not read as a mismatch")
    }

    @Test
    void testAStaleEdgeCertificateIsAMismatch() {
        CertificateVerification result =
                verifierSeeing { String host -> "054111f02fca3516b4ff23024486b7558bf3" }.verify(certificate())
        assertFalse(result.matches)
        assertTrue(result.detail.contains("still serving a different certificate"))
    }

    @Test
    void testACertificateWithNoRecordedSerialIsNotChecked() {
        boolean probed = false
        CertificateVerification result = verifierSeeing { String host -> probed = true; SERIAL }
                .verify(certificate(null))
        assertFalse(result.matches)
        assertFalse(probed)
        assertNull(result.observedSerial)
    }

    @Test
    void testAnUnreachableHostIsReportedRatherThanThrown() {
        CertificateVerification result =
                verifierSeeing { String host -> throw new IOException("connection refused") }.verify(certificate())
        assertFalse(result.matches)
        assertTrue(result.detail.contains("unable to read a certificate"))
    }
}
