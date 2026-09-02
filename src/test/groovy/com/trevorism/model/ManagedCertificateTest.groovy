package com.trevorism.model

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

class ManagedCertificateTest {

    @Test
    void testChallengeLabelIsZoneRelative() {
        ManagedCertificate certificate = new ManagedCertificate(category: "action")
        assertEquals("_acme-challenge.action", certificate.challengeLabel)
    }

    @Test
    void testChallengeFqdnIsFullyQualified() {
        ManagedCertificate certificate = new ManagedCertificate(category: "draw")
        assertEquals("_acme-challenge.draw.trevorism.com", certificate.challengeFqdn)
    }

    @Test
    void testCertificatesAreEnabledByDefault() {
        assertTrue(new ManagedCertificate().enabled)
    }

    @Test
    void testRotationRequestDefaultsToProduction() {
        RotationRequest request = new RotationRequest()
        assertEquals(RotationRequest.PRODUCTION, request.acmeServer)
        assertFalse(request.isStaging())
        assertFalse(request.force)
        assertEquals(30, request.minDaysRemaining)
    }

    @Test
    void testRotationRequestDetectsStaging() {
        assertTrue(new RotationRequest(acmeServer: "staging").isStaging())
        assertTrue(new RotationRequest(acmeServer: "Staging").isStaging())
    }

    @Test
    void testRotationEventStampsStateAndTime() {
        RotationEvent event = new RotationEvent(RotationState.UPLOADED, "some detail")
        assertEquals("UPLOADED", event.state)
        assertEquals("some detail", event.detail)
        assertTrue(event.at.endsWith("Z"))
    }
}
