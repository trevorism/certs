package com.trevorism.service

import com.trevorism.crypto.SerialNumbers
import com.trevorism.model.CertificateVerification
import com.trevorism.model.ManagedCertificate
import jakarta.inject.Inject
import jakarta.inject.Singleton

import java.time.Instant

@Singleton
class DefaultCertificateVerifier implements CertificateVerifier {

    @Inject
    EdgeCertificateInspector edgeCertificateInspector

    @Override
    CertificateVerification verify(ManagedCertificate certificate) {
        CertificateVerification verification = new CertificateVerification(
                certificateId: certificate.id,
                wildcard: certificate.wildcard,
                probeHost: certificate.probeHost,
                expectedSerial: certificate.serial,
                checkedAt: Instant.now().toString())

        if (!certificate.serial) {
            verification.detail = "no rotation has recorded a serial for this certificate yet"
            return verification
        }

        try {
            verification.observedSerial = edgeCertificateInspector.readSerial(certificate.probeHost)
            verification.matches = SerialNumbers.same(certificate.serial, verification.observedSerial)
            verification.detail = verification.matches ?
                    "${certificate.probeHost} is serving the expected certificate" :
                    "${certificate.probeHost} is still serving a different certificate"
        } catch (Exception e) {
            verification.detail = "unable to read a certificate from ${certificate.probeHost}: ${e.message}"
        }
        return verification
    }
}
