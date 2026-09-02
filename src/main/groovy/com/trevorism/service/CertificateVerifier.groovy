package com.trevorism.service

import com.trevorism.model.CertificateVerification
import com.trevorism.model.ManagedCertificate

interface CertificateVerifier {

    CertificateVerification verify(ManagedCertificate certificate)
}
