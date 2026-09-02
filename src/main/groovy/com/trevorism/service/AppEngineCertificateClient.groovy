package com.trevorism.service

import com.trevorism.model.AuthorizedCertificate

interface AppEngineCertificateClient {

    AuthorizedCertificate findByDomain(String gcpProject, String wildcard)

    AuthorizedCertificate describe(String gcpProject, String certificateId)

    void replaceCertificateMaterial(String gcpProject, String certificateId, String chainPem, String privateKeyPem)
}
