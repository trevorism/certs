package com.trevorism.service

import com.trevorism.model.AuthorizedCertificate
import com.trevorism.model.CertificateVerification
import com.trevorism.model.ManagedCertificate
import com.trevorism.model.RotationRequest
import com.trevorism.model.RotationRun
import com.trevorism.model.SweepResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import java.time.Instant
import java.time.temporal.ChronoUnit

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assertions.assertTrue

class DefaultCertificateSweepServiceTest {

    private DefaultCertificateSweepService service
    private List<ManagedCertificate> registry
    private Map<String, String> expiryByProject
    private List<String> rotated
    private List<Map> reported
    private Map<String, Boolean> edgeMatches

    private static String daysOut(int days) {
        return Instant.now().plus(days, ChronoUnit.DAYS).toString()
    }

    private static ManagedCertificate cert(String category, String id) {
        return new ManagedCertificate(id: id, category: category, wildcard: "*.${category}.trevorism.com",
                gcpProject: "trevorism-${category}", appEngineCertificateId: "ae-${category}",
                probeHost: "probe.${category}.trevorism.com", enabled: true, serial: "aaa")
    }

    @BeforeEach
    void setup() {
        registry = [cert("action", "1"), cert("draw", "2"), cert("project", "3")]
        expiryByProject = ["trevorism-action": daysOut(90), "trevorism-draw": daysOut(90),
                           "trevorism-project": daysOut(90)]
        rotated = []
        reported = []
        edgeMatches = [:].withDefault { true }

        service = new DefaultCertificateSweepService()
        service.managedCertificateService = [
                list  : { registry },
                get   : { String id -> registry.find { it.id == id } },
                create: { ManagedCertificate c -> c },
                update: { String id, ManagedCertificate c -> c }
        ] as ManagedCertificateService

        service.appEngineCertificateClient = [
                findByDomain              : { String p, String w -> new AuthorizedCertificate(id: "x") },
                describe                  : { String p, String id ->
                    if (expiryByProject[p] == null) {
                        throw new IllegalStateException("boom")
                    }
                    new AuthorizedCertificate(id: id, expireTime: expiryByProject[p])
                },
                replaceCertificateMaterial: { String p, String id, String c, String k -> }
        ] as AppEngineCertificateClient

        service.certificateRotationService = [
                rotate: { String id, RotationRequest request ->
                    rotated << id
                    new RotationRun(id: "run-${id}", certificateId: id, outcome: "COMPLETED")
                }
        ] as CertificateRotationService

        service.certificateVerifier = [
                verify: { ManagedCertificate c ->
                    new CertificateVerification(certificateId: c.id, wildcard: c.wildcard,
                            probeHost: c.probeHost, expectedSerial: c.serial, matches: edgeMatches[c.category])
                }
        ] as CertificateVerifier

        service.failureReporter = [
                report: { String message, Map details -> reported << [message: message, details: details] }
        ] as FailureReporter
    }

    @Test
    void testNothingDueRotatesNothing() {
        SweepResult result = service.sweep()
        assertEquals(0, result.dueCount)
        assertEquals([], rotated)
        assertTrue(result.notes.any { it.contains("renewal window") })
    }

    @Test
    void testOnlyOneCertificateIsRotatedPerSweep() {
        expiryByProject["trevorism-action"] = daysOut(10)
        expiryByProject["trevorism-draw"] = daysOut(20)
        SweepResult result = service.sweep()
        assertEquals(2, result.dueCount)
        assertEquals(["1"], rotated)
        assertEquals("*.action.trevorism.com", result.rotatedWildcard)
    }

    @Test
    void testTheMostUrgentCertificateWins() {
        expiryByProject["trevorism-action"] = daysOut(25)
        expiryByProject["trevorism-draw"] = daysOut(5)
        service.sweep()
        assertEquals(["2"], rotated)
    }

    @Test
    void testProjectIsNeverChosenWhileAnythingElseIsDue() {
        expiryByProject["trevorism-project"] = daysOut(1)
        expiryByProject["trevorism-draw"] = daysOut(29)
        service.sweep()
        assertEquals(["2"], rotated, "project hosts godaddy, github and certs, so it must rotate last")
    }

    @Test
    void testProjectIsRotatedWhenItIsTheOnlyOneDue() {
        expiryByProject["trevorism-project"] = daysOut(10)
        service.sweep()
        assertEquals(["3"], rotated)
    }

    @Test
    void testAnUnreadableExpiryIsReportedAndDoesNotStopTheSweep() {
        expiryByProject.remove("trevorism-action")
        expiryByProject["trevorism-draw"] = daysOut(10)
        SweepResult result = service.sweep()
        assertEquals(["2"], rotated)
        assertTrue(reported.any { it.message.contains("Cannot read the expiry") })
        assertTrue(result.notes.any { it.contains("unable to read the expiry") })
    }

    @Test
    void testAnImminentExpiryIsReported() {
        expiryByProject["trevorism-draw"] = daysOut(9)
        service.sweep()
        assertTrue(reported.any { it.message.contains("expires in") && it.message.contains("draw") })
    }

    @Test
    void testEdgeDriftIsReportedOnceTheGraceHasPassed() {
        registry.find { it.category == "draw" }.lastRotatedAt = Instant.now().minus(48, ChronoUnit.HOURS).toString()
        edgeMatches["draw"] = false
        service.sweep()
        assertTrue(reported.any { it.message.contains("Edge is not serving the latest") })
    }

    @Test
    void testEdgeDriftIsToleratedInsideTheGrace() {
        registry.find { it.category == "draw" }.lastRotatedAt = Instant.now().minus(1, ChronoUnit.HOURS).toString()
        edgeMatches["draw"] = false
        service.sweep()
        assertTrue(reported.every { !it.message.contains("Edge is not serving") })
    }

    @Test
    void testDisabledCertificatesAreIgnoredEntirely() {
        registry.find { it.category == "draw" }.enabled = false
        expiryByProject["trevorism-draw"] = daysOut(1)
        SweepResult result = service.sweep()
        assertEquals(2, result.enabledCount)
        assertEquals([], rotated)
    }

    @Test
    void testEveryEnabledCertificateIsVerified() {
        SweepResult result = service.sweep()
        assertEquals(3, result.verifications.size())
    }

    @Test
    void testACertificateWithNoSerialNeverReportsDrift() {
        registry.find { it.category == "draw" }.serial = null
        registry.find { it.category == "draw" }.lastRotatedAt = Instant.now().minus(48, ChronoUnit.HOURS).toString()
        edgeMatches["draw"] = false
        service.sweep()
        assertTrue(reported.every { !it.message.contains("Edge is not serving") })
    }

    @Test
    void testDriftGraceIsFalseWithoutARotationTimestamp() {
        assertEquals(false, DefaultCertificateSweepService.withinDriftGrace(new ManagedCertificate()))
    }

    @Test
    void testRotationOutcomeIsRecorded() {
        expiryByProject["trevorism-draw"] = daysOut(10)
        SweepResult result = service.sweep()
        assertEquals("COMPLETED", result.rotationOutcome)
        assertEquals("run-2", result.rotationRunId)
        assertNull(result.notes.find { it.contains("could not start") })
    }
}
