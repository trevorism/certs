package com.trevorism.service

import com.trevorism.crypto.IssuedCertificateValidator
import com.trevorism.crypto.PrivateKeyConverter
import com.trevorism.model.AuthorizedCertificate
import com.trevorism.model.IssuedCertificate
import com.trevorism.model.ManagedCertificate
import com.trevorism.model.RotationEvent
import com.trevorism.model.RotationRequest
import com.trevorism.model.RotationRun
import com.trevorism.model.RotationState
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.time.Instant
import java.time.temporal.ChronoUnit

@Singleton
class DefaultCertificateRotationService implements CertificateRotationService {

    static final String ZONE = "trevorism.com"
    static final String ZONE_SUFFIX = ".trevorism.com"

    private static final Logger log = LoggerFactory.getLogger(DefaultCertificateRotationService)

    @Inject
    ManagedCertificateService managedCertificateService

    @Inject
    RotationRunService rotationRunService

    @Inject
    AppEngineCertificateClient appEngineCertificateClient

    @Inject
    CertificateIssuer certificateIssuer

    @Inject
    ChallengeDnsService challengeDnsService

    @Inject
    PropagationChecker propagationChecker

    @Override
    RotationRun rotate(String certificateId, RotationRequest request) {
        ManagedCertificate certificate = managedCertificateService.get(certificateId)
        if (!certificate) {
            throw new IllegalArgumentException("No managed certificate with id ${certificateId}")
        }
        if (!certificate.enabled) {
            throw new IllegalStateException("The managed certificate ${certificate.wildcard} is disabled")
        }

        RotationRun run = startRun(certificate, request)
        try {
            AuthorizedCertificate authorized = preflight(certificate, run)
            if (!request.force && hasEnoughLifeLeft(authorized, request.minDaysRemaining)) {
                return finish(run, RotationState.SKIPPED,
                        "expires ${authorized.expireTime}, more than ${request.minDaysRemaining} days away")
            }

            IssuedCertificate issued = certificateIssuer.issue(
                    certificate.wildcard, request.acmeServer, buildChallengeHandler(certificate, run))
            record(run, RotationState.ISSUED, "serial ${issued.serial} expiring ${issued.notAfter}")

            if (request.isStaging()) {
                return finish(run, RotationState.COMPLETED, "staging issuance succeeded, nothing was uploaded")
            }

            IssuedCertificateValidator.validateForUpload(issued, certificate.wildcard)
            record(run, RotationState.FORMAT_VERIFIED, "issued by ${issued.issuer}")

            String privateKeyPem = PrivateKeyConverter.toPkcs1Pem(issued.keyPair.getPrivate())
            appEngineCertificateClient.replaceCertificateMaterial(
                    certificate.gcpProject, authorized.id, issued.chainPem, privateKeyPem)
            record(run, RotationState.UPLOADED, "replaced material on ${authorized.id}")

            applyResultToCertificate(certificate, authorized, issued, run)
            return finish(run, RotationState.COMPLETED, "rotated to serial ${issued.serial}")
        } catch (Exception e) {
            log.error("Rotation of ${certificate.wildcard} failed", e)
            return fail(run, e)
        }
    }

    private AuthorizedCertificate preflight(ManagedCertificate certificate, RotationRun run) {
        AuthorizedCertificate found = appEngineCertificateClient.findByDomain(certificate.gcpProject, certificate.wildcard)
        AuthorizedCertificate described = appEngineCertificateClient.describe(certificate.gcpProject, found.id)
        described.id = described.id ?: found.id
        requireServedByExpectedMapping(described, certificate)
        record(run, RotationState.PREFLIGHT_OK,
                "certificate ${described.id} serving ${described.visibleDomainMappings}, expires ${described.expireTime}")
        return described
    }

    private Dns01ChallengeHandler buildChallengeHandler(ManagedCertificate certificate, RotationRun run) {
        return new Dns01ChallengeHandler() {

            @Override
            void publish(String recordName, String digest) {
                String label = toZoneRelativeLabel(recordName, certificate)
                challengeDnsService.setChallenge(label, digest)
                record(run, RotationState.CHALLENGE_SET, "wrote the challenge to ${label}")
                propagationChecker.awaitPropagation("${label}${ZONE_SUFFIX}", digest)
                record(run, RotationState.PROPAGATED, "the challenge is visible on every authoritative nameserver")
            }

            @Override
            void cleanup(String recordName) {
                try {
                    challengeDnsService.clearChallenge(toZoneRelativeLabel(recordName, certificate))
                } catch (Exception e) {
                    log.warn("Unable to remove the challenge record for ${certificate.wildcard}: ${e.message}")
                }
            }
        }
    }

    static String expectedMappingName(ManagedCertificate certificate) {
        return "apps/${certificate.gcpProject}/domainMappings/${certificate.wildcard}"
    }

    static void requireServedByExpectedMapping(AuthorizedCertificate described, ManagedCertificate certificate) {
        String expected = expectedMappingName(certificate)
        if (!described.visibleDomainMappings?.contains(expected)) {
            throw new IllegalStateException(
                    "Certificate ${described.id} is not served by ${expected}; it is bound to ${described.visibleDomainMappings}")
        }
    }

    static String toZoneRelativeLabel(String recordName, ManagedCertificate certificate) {
        if (!recordName?.endsWith(ZONE_SUFFIX)) {
            throw new IllegalStateException("The acme challenge record ${recordName} falls outside the ${ZONE} zone")
        }
        String label = recordName.substring(0, recordName.length() - ZONE_SUFFIX.length())
        if (label != certificate.challengeLabel) {
            throw new IllegalStateException(
                    "Expected a challenge at ${certificate.challengeLabel} but acme asked for ${label}")
        }
        return label
    }

    static boolean hasEnoughLifeLeft(AuthorizedCertificate authorized, int minDaysRemaining) {
        if (!authorized?.expireTime) {
            return false
        }
        return Instant.parse(authorized.expireTime).isAfter(Instant.now().plus(minDaysRemaining, ChronoUnit.DAYS))
    }

    private RotationRun startRun(ManagedCertificate certificate, RotationRequest request) {
        RotationRun run = new RotationRun(
                certificateId: certificate.id,
                wildcard: certificate.wildcard,
                gcpProject: certificate.gcpProject,
                acmeServer: request.acmeServer,
                startedAt: Instant.now().toString(),
                outcome: RotationState.REQUESTED.name(),
                events: [new RotationEvent(RotationState.REQUESTED, "force=${request.force}")])
        return rotationRunService.create(run)
    }

    private void record(RotationRun run, RotationState state, String detail) {
        log.info("${run.wildcard}: ${state} ${detail}")
        run.events << new RotationEvent(state, detail)
        run.outcome = state.name()
        rotationRunService.update(run.id, run)
    }

    private RotationRun finish(RotationRun run, RotationState state, String detail) {
        run.events << new RotationEvent(state, detail)
        run.outcome = state.name()
        run.finishedAt = Instant.now().toString()
        return rotationRunService.update(run.id, run)
    }

    private RotationRun fail(RotationRun run, Exception e) {
        run.failureDetail = "${e.class.simpleName}: ${e.message}"
        return finish(run, RotationState.FAILED, run.failureDetail)
    }

    private void applyResultToCertificate(ManagedCertificate certificate, AuthorizedCertificate authorized,
                                          IssuedCertificate issued, RotationRun run) {
        run.newSerial = issued.serial
        run.newNotAfter = issued.notAfter
        certificate.appEngineCertificateId = authorized.id
        certificate.serial = issued.serial
        certificate.notAfter = issued.notAfter
        certificate.lastRotationRunId = run.id
        certificate.lastRotatedAt = Instant.now().toString()
        certificate.lastOutcome = RotationState.COMPLETED.name()
        managedCertificateService.update(certificate.id, certificate)
    }
}
