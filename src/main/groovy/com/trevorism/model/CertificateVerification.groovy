package com.trevorism.model

class CertificateVerification {

    String certificateId
    String wildcard
    String probeHost
    String expectedSerial
    String observedSerial
    boolean matches
    boolean probeFailed
    String detail
    String checkedAt

    boolean isConclusive() {
        return !probeFailed && expectedSerial
    }

    boolean isDrifting() {
        return isConclusive() && !matches
    }
}
