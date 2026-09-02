package com.trevorism.service

import com.trevorism.model.ManagedCertificate

interface ManagedCertificateService {

    List<ManagedCertificate> list()

    ManagedCertificate get(String id)

    ManagedCertificate create(ManagedCertificate certificate)

    ManagedCertificate update(String id, ManagedCertificate certificate)
}
