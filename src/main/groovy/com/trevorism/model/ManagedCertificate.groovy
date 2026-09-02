package com.trevorism.model

class ManagedCertificate {

    String id
    String category
    String wildcard
    String gcpProject
    String appEngineCertificateId
    String probeHost
    String notAfter
    String serial
    boolean enabled = true
    String lastRotationRunId
    String lastRotatedAt
    String lastOutcome

    String getChallengeLabel() {
        return "_acme-challenge.${category}"
    }

    String getChallengeFqdn() {
        return "_acme-challenge.${category}.trevorism.com"
    }
}
