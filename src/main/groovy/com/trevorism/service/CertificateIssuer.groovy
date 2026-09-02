package com.trevorism.service

import com.trevorism.model.IssuedCertificate

interface CertificateIssuer {

    IssuedCertificate issue(String wildcard, String acmeServer, Dns01ChallengeHandler challengeHandler)
}
