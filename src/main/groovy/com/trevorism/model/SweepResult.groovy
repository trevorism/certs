package com.trevorism.model

class SweepResult {

    String startedAt
    String finishedAt
    int enabledCount
    int dueCount
    String rotatedCertificateId
    String rotatedWildcard
    String rotationRunId
    String rotationOutcome
    List<CertificateVerification> verifications = []
    List<String> notes = []
}
