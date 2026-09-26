package com.trevorism.service

import com.trevorism.model.AuthorizedCertificate
import com.trevorism.model.CertificateVerification
import com.trevorism.model.ManagedCertificate
import com.trevorism.model.RotationRequest
import com.trevorism.model.RotationRun
import com.trevorism.model.SweepResult
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

@Singleton
class DefaultCertificateSweepService implements CertificateSweepService {

    static final int MIN_DAYS_REMAINING = 24
    static final int URGENT_DAYS_REMAINING = 12
    static final int DRIFT_GRACE_HOURS = 25
    static final String LAST_CATEGORY = "project"

    private static final Logger log = LoggerFactory.getLogger(DefaultCertificateSweepService)

    @Inject
    ManagedCertificateService managedCertificateService

    @Inject
    AppEngineCertificateClient appEngineCertificateClient

    @Inject
    CertificateRotationService certificateRotationService

    @Inject
    CertificateVerifier certificateVerifier

    @Inject
    FailureReporter failureReporter

    @Override
    SweepResult sweep() {
        SweepResult result = new SweepResult(startedAt: Instant.now().toString())
        List<ManagedCertificate> enabled = listEnabled()
        result.enabledCount = enabled.size()

        List<Map> due = findDueCertificates(enabled, result)
        result.dueCount = due.size()

        if (due) {
            rotateMostUrgent(due, result)
        } else {
            result.addNote("no certificate is inside the ${MIN_DAYS_REMAINING} day renewal window")
        }

        verifyEdge(result.rotatedCertificateId ? listEnabled() : enabled, result)
        result.finishedAt = Instant.now().toString()
        return result
    }

    private List<ManagedCertificate> listEnabled() {
        return managedCertificateService.list().findAll { it.enabled }
    }

    private List<Map> findDueCertificates(List<ManagedCertificate> enabled, SweepResult result) {
        List<Map> due = []
        enabled.each { ManagedCertificate certificate ->
            try {
                AuthorizedCertificate described = appEngineCertificateClient
                        .describe(certificate.gcpProject, certificate.appEngineCertificateId)
                if (!DefaultCertificateRotationService.hasEnoughLifeLeft(described, MIN_DAYS_REMAINING)) {
                    due << [certificate: certificate, expireTime: described.expireTime ?: ""]
                }
                reportIfUrgent(certificate, described, result)
            } catch (Exception e) {
                String note = "unable to read the expiry of ${certificate.wildcard}: ${e.message}"
                log.warn(note)
                result.addNote(note)
                failureReporter.report("Cannot read the expiry of ${certificate.wildcard}", [
                        wildcard     : certificate.wildcard,
                        gcpProject   : certificate.gcpProject,
                        certificateId: certificate.appEngineCertificateId,
                        failure      : e.message])
            }
        }
        return sortWithProjectLast(due)
    }

    static List<Map> sortWithProjectLast(List<Map> due) {
        return due.sort(false) { Map left, Map right ->
            int byRank = rankOf(left) <=> rankOf(right)
            return byRank != 0 ? byRank : (left.expireTime <=> right.expireTime)
        }
    }

    private static int rankOf(Map entry) {
        return ((ManagedCertificate) entry.certificate).category == LAST_CATEGORY ? 1 : 0
    }

    private void reportIfUrgent(ManagedCertificate certificate, AuthorizedCertificate described, SweepResult result) {
        if (!described?.expireTime) {
            return
        }
        Instant expiry = Instant.parse(described.expireTime)
        if (expiry.isAfter(Instant.now().plus(URGENT_DAYS_REMAINING, ChronoUnit.DAYS))) {
            return
        }
        long daysLeft = Duration.between(Instant.now(), expiry).toDays()
        result.addNote("${certificate.wildcard} expires in ${daysLeft} days")
        failureReporter.report("Certificate ${certificate.wildcard} expires in ${daysLeft} days", [
                wildcard   : certificate.wildcard,
                gcpProject : certificate.gcpProject,
                expireTime : described.expireTime,
                daysLeft   : daysLeft])
    }

    private void rotateMostUrgent(List<Map> due, SweepResult result) {
        ManagedCertificate target = (ManagedCertificate) due.first().certificate
        result.rotatedCertificateId = target.id
        result.rotatedWildcard = target.wildcard
        log.info("Sweep is rotating ${target.wildcard}, ${due.size()} certificate(s) are due")
        try {
            RotationRun run = certificateRotationService.rotate(target.id,
                    new RotationRequest(minDaysRemaining: MIN_DAYS_REMAINING))
            result.rotationRunId = run.id
            result.rotationOutcome = run.outcome
        } catch (Exception e) {
            result.rotationOutcome = "FAILED"
            result.addNote("rotation of ${target.wildcard} could not start: ${e.message}")
            failureReporter.report("Certificate rotation could not start for ${target.wildcard}", [
                    wildcard     : target.wildcard,
                    certificateId: target.id,
                    failure      : e.message])
        }
    }

    private void verifyEdge(List<ManagedCertificate> enabled, SweepResult result) {
        enabled.each { ManagedCertificate certificate ->
            try {
                result.verifications << inspectEdge(certificate)
            } catch (Exception e) {
                String note = "unable to verify the edge for ${certificate.wildcard}: ${e.message}"
                log.warn(note)
                result.addNote(note)
                failureReporter.report("Cannot verify the edge for ${certificate.wildcard}", [
                        wildcard : certificate.wildcard,
                        probeHost: certificate.probeHost,
                        failure  : e.message])
            }
        }
    }

    private CertificateVerification inspectEdge(ManagedCertificate certificate) {
        CertificateVerification verification = certificateVerifier.verify(certificate)
        if (verification.probeFailed) {
            reportProbeFailure(certificate, verification)
            return verification
        }
        if (!verification.drifting || withinDriftGrace(certificate)) {
            return verification
        }
        failureReporter.report("Edge is not serving the latest certificate for ${certificate.wildcard}", [
                wildcard      : certificate.wildcard,
                probeHost     : certificate.probeHost,
                expectedSerial: verification.expectedSerial,
                observedSerial: verification.observedSerial,
                lastRotatedAt : certificate.lastRotatedAt])
        return verification
    }

    private void reportProbeFailure(ManagedCertificate certificate, CertificateVerification verification) {
        log.warn("Unable to probe the edge for ${certificate.wildcard}: ${verification.detail}")
        failureReporter.report("Unable to probe the edge for ${certificate.wildcard}", [
                wildcard : certificate.wildcard,
                probeHost: certificate.probeHost,
                failure  : verification.detail])
    }

    static boolean withinDriftGrace(ManagedCertificate certificate) {
        if (!certificate.lastRotatedAt) {
            return false
        }
        try {
            return Instant.parse(certificate.lastRotatedAt)
                    .isAfter(Instant.now().minus(DRIFT_GRACE_HOURS, ChronoUnit.HOURS))
        } catch (Exception ignored) {
            return false
        }
    }
}
