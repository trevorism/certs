package com.trevorism.model

class CertificateVerification {

    String certificateId
    String wildcard
    String probeHost
    String expectedSerial
    String observedSerial
    boolean matches
    String detail
    String checkedAt
}
