package com.trevorism.service

import com.trevorism.CertificateTestFactory
import com.trevorism.model.AuthorizedCertificate
import com.trevorism.model.DnsRecord
import com.trevorism.model.IssuedCertificate
import com.trevorism.model.ManagedCertificate
import com.trevorism.model.RotationRequest
import com.trevorism.model.RotationRun
import com.trevorism.model.RotationState
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import java.time.Instant
import java.time.temporal.ChronoUnit

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class DefaultCertificateRotationServiceTest {

    private static final String CHALLENGE_FQDN = "_acme-challenge.action.trevorism.com"

    private DefaultCertificateRotationService service
    private ManagedCertificate certificate
    private List<String> dnsCalls
    private List<String> propagationWaits
    private Map uploaded
    private ManagedCertificate savedCertificate
    private boolean issuerCalled

    @BeforeEach
    void setup() {
        issuerCalled = false
        certificate = new ManagedCertificate(
                id: "cert-1", category: "action", wildcard: CertificateTestFactory.WILDCARD,
                gcpProject: "trevorism-action", probeHost: "alert.action.trevorism.com", enabled: true)
        dnsCalls = []
        propagationWaits = []
        uploaded = null
        savedCertificate = null

        service = new DefaultCertificateRotationService()
        service.managedCertificateService = [
                get   : { String id -> id == "cert-1" ? certificate : null },
                update: { String id, ManagedCertificate c -> savedCertificate = c; c },
                list  : { [certificate] },
                create: { ManagedCertificate c -> c }
        ] as ManagedCertificateService

        service.rotationRunService = [
                create: { RotationRun r -> r.id = "run-1"; r },
                update: { String id, RotationRun r -> r },
                get   : { String id -> null },
                list  : { [] }
        ] as RotationRunService

        service.challengeDnsService = [
                setChallenge  : { String label, String digest -> dnsCalls << "set ${label}=${digest}".toString() },
                clearChallenge: { String label -> dnsCalls << "clear ${label}".toString() },
                readChallenge : { String label -> [] }
        ] as ChallengeDnsService

        service.propagationChecker = [
                awaitPropagation: { String fqdn, String value -> propagationWaits << "${fqdn}=${value}".toString() }
        ] as PropagationChecker

        service.appEngineCertificateClient = clientExpiring(Instant.now().plus(5, ChronoUnit.DAYS).toString())
        service.certificateIssuer = issuerReturning(CertificateTestFactory.issued())
    }

    private AppEngineCertificateClient clientExpiring(String expireTime, List<String> visible = null) {
        List<String> mappings = visible != null ? visible :
                ["apps/trevorism-action/domainMappings/${CertificateTestFactory.WILDCARD}".toString()]
        return [
                findByDomain            : { String p, String w -> new AuthorizedCertificate(id: "ae-cert-id") },
                describe                : { String p, String id ->
                    new AuthorizedCertificate(id: id, expireTime: expireTime,
                            domainMappingsCount: mappings.size(), visibleDomainMappings: mappings)
                },
                replaceCertificateMaterial: { String p, String id, String chain, String key ->
                    uploaded = [project: p, id: id, chain: chain, key: key]
                }
        ] as AppEngineCertificateClient
    }

    private CertificateIssuer issuerReturning(IssuedCertificate issued) {
        return [
                issue: { String wildcard, String server, Dns01ChallengeHandler handler ->
                    issuerCalled = true
                    handler.publish(CHALLENGE_FQDN, "digest-value")
                    handler.cleanup(CHALLENGE_FQDN)
                    return issued
                }
        ] as CertificateIssuer
    }

    private ChallengeDnsService dnsServiceLeaving(List<DnsRecord> leftovers, boolean failOnClear = false) {
        return [
                setChallenge  : { String label, String digest -> dnsCalls << "set ${label}=${digest}".toString() },
                clearChallenge: { String label ->
                    dnsCalls << "clear ${label}".toString()
                    if (failOnClear) {
                        throw new IllegalStateException("403 Forbidden from godaddy")
                    }
                },
                readChallenge : { String label -> leftovers }
        ] as ChallengeDnsService
    }

    @Test
    void testHappyPathCompletesAndUploads() {
        RotationRun run = service.rotate("cert-1", new RotationRequest())
        assertEquals(RotationState.COMPLETED.name(), run.outcome)
        assertNotNull(uploaded)
        assertEquals("trevorism-action", uploaded.project)
        assertEquals("ae-cert-id", uploaded.id)
    }

    @Test
    void testUploadedKeyIsInTraditionalPkcs1Form() {
        service.rotate("cert-1", new RotationRequest())
        assertTrue(uploaded.key.startsWith("-----BEGIN RSA PRIVATE KEY-----"))
    }

    @Test
    void testUploadedChainIsTheFullChain() {
        service.rotate("cert-1", new RotationRequest())
        assertEquals(2, uploaded.chain.count("BEGIN CERTIFICATE"))
    }

    @Test
    void testChallengeIsWrittenAndCleanedUpAtTheZoneRelativeLabel() {
        service.rotate("cert-1", new RotationRequest())
        assertEquals(["clear _acme-challenge.action",
                      "set _acme-challenge.action=digest-value",
                      "clear _acme-challenge.action"], dnsCalls)
    }

    @Test
    void testTheDnsWritePathIsProvenBeforeAnyAcmeOrderExists() {
        service.challengeDnsService = dnsServiceLeaving([], true)
        RotationRun run = service.rotate("cert-1", new RotationRequest())
        assertEquals(RotationState.FAILED.name(), run.outcome)
        assertFalse(issuerCalled, "no acme order should be created when the dns write path is unreachable")
        assertEquals(["clear _acme-challenge.action"], dnsCalls)
        assertNull(uploaded)
    }

    @Test
    void testLeftoverRecordsAfterClearingAbortBeforeIssuance() {
        service.challengeDnsService = dnsServiceLeaving([new DnsRecord(name: "_acme-challenge.action", data: "stale")])
        RotationRun run = service.rotate("cert-1", new RotationRequest())
        assertEquals(RotationState.FAILED.name(), run.outcome)
        assertFalse(issuerCalled)
        assertTrue(run.failureDetail.contains("still present"))
    }

    @Test
    void testAFreshCertificateNeverTouchesDns() {
        service.appEngineCertificateClient = clientExpiring(Instant.now().plus(60, ChronoUnit.DAYS).toString())
        service.rotate("cert-1", new RotationRequest())
        assertEquals([], dnsCalls)
        assertFalse(issuerCalled)
    }

    @Test
    void testPropagationIsAwaitedOnTheFullyQualifiedName() {
        service.rotate("cert-1", new RotationRequest())
        assertEquals(["${CHALLENGE_FQDN}=digest-value".toString()], propagationWaits)
    }

    @Test
    void testCertificateRecordIsUpdatedAfterUpload() {
        service.rotate("cert-1", new RotationRequest())
        assertNotNull(savedCertificate)
        assertEquals("ae-cert-id", savedCertificate.appEngineCertificateId)
        assertEquals("run-1", savedCertificate.lastRotationRunId)
        assertEquals(RotationState.COMPLETED.name(), savedCertificate.lastOutcome)
    }

    @Test
    void testStagingIssuanceNeverUploads() {
        RotationRun run = service.rotate("cert-1", new RotationRequest(acmeServer: RotationRequest.STAGING))
        assertEquals(RotationState.COMPLETED.name(), run.outcome)
        assertNull(uploaded)
    }

    @Test
    void testAFreshCertificateIsSkipped() {
        service.appEngineCertificateClient = clientExpiring(Instant.now().plus(60, ChronoUnit.DAYS).toString())
        RotationRun run = service.rotate("cert-1", new RotationRequest())
        assertEquals(RotationState.SKIPPED.name(), run.outcome)
        assertNull(uploaded)
        assertEquals([], dnsCalls)
    }

    @Test
    void testForceRotatesEvenWhenFresh() {
        service.appEngineCertificateClient = clientExpiring(Instant.now().plus(60, ChronoUnit.DAYS).toString())
        RotationRun run = service.rotate("cert-1", new RotationRequest(force: true))
        assertEquals(RotationState.COMPLETED.name(), run.outcome)
        assertNotNull(uploaded)
    }

    @Test
    void testAnUnboundCertificateFailsBeforeAnyAcmeWork() {
        service.appEngineCertificateClient = clientExpiring(Instant.now().plus(5, ChronoUnit.DAYS).toString(), [])
        RotationRun run = service.rotate("cert-1", new RotationRequest())
        assertEquals(RotationState.FAILED.name(), run.outcome)
        assertEquals([], dnsCalls)
        assertNull(uploaded)
    }

    @Test
    void testACertificateServedByAnotherProjectIsRejected() {
        service.appEngineCertificateClient = clientExpiring(Instant.now().plus(5, ChronoUnit.DAYS).toString(),
                ["apps/trevorism-draw/domainMappings/*.draw.trevorism.com"])
        RotationRun run = service.rotate("cert-1", new RotationRequest())
        assertEquals(RotationState.FAILED.name(), run.outcome)
        assertEquals([], dnsCalls)
        assertNull(uploaded)
        assertTrue(run.failureDetail.contains("is not served by"))
    }

    @Test
    void testExpectedMappingNameMatchesTheAppEngineFormat() {
        assertEquals("apps/trevorism-action/domainMappings/*.action.trevorism.com",
                DefaultCertificateRotationService.expectedMappingName(certificate))
    }

    @Test
    void testAStagingIssuerIsNeverUploadedToProduction() {
        service.certificateIssuer = issuerReturning(
                CertificateTestFactory.issued(issuerName: CertificateTestFactory.STAGING_CA_NAME))
        RotationRun run = service.rotate("cert-1", new RotationRequest())
        assertEquals(RotationState.FAILED.name(), run.outcome)
        assertNull(uploaded)
        assertTrue(run.failureDetail.contains("issuerIsNotStaging"))
    }

    @Test
    void testUnknownCertificateIsRejected() {
        assertThrows(IllegalArgumentException) { service.rotate("nope", new RotationRequest()) }
    }

    @Test
    void testDisabledCertificateIsRejected() {
        certificate.enabled = false
        assertThrows(IllegalStateException) { service.rotate("cert-1", new RotationRequest()) }
    }

    @Test
    void testZoneRelativeLabelIsDerivedFromTheAcmeRecordName() {
        assertEquals("_acme-challenge.action",
                DefaultCertificateRotationService.toZoneRelativeLabel(CHALLENGE_FQDN, certificate))
    }

    @Test
    void testARecordNameOutsideTheZoneIsRejected() {
        assertThrows(IllegalStateException) {
            DefaultCertificateRotationService.toZoneRelativeLabel("_acme-challenge.action.example.com", certificate)
        }
    }

    @Test
    void testARecordNameForAnotherCategoryIsRejected() {
        assertThrows(IllegalStateException) {
            DefaultCertificateRotationService.toZoneRelativeLabel("_acme-challenge.datastore.trevorism.com", certificate)
        }
    }

    @Test
    void testLifeLeftIsFalseWithoutAnExpiry() {
        assertEquals(false, DefaultCertificateRotationService.hasEnoughLifeLeft(new AuthorizedCertificate(), 30))
    }

    @Test
    void testLifeLeftBoundary() {
        String soon = Instant.now().plus(29, ChronoUnit.DAYS).toString()
        String later = Instant.now().plus(31, ChronoUnit.DAYS).toString()
        assertEquals(false, DefaultCertificateRotationService.hasEnoughLifeLeft(new AuthorizedCertificate(expireTime: soon), 30))
        assertEquals(true, DefaultCertificateRotationService.hasEnoughLifeLeft(new AuthorizedCertificate(expireTime: later), 30))
    }
}
