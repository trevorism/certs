package com.trevorism.service

import com.trevorism.data.FastDatastoreRepository
import com.trevorism.data.Repository
import com.trevorism.https.AppClientSecureHttpClient
import com.trevorism.https.SecureHttpClient
import com.trevorism.model.ManagedCertificate
import jakarta.inject.Singleton

@Singleton
class DatastoreManagedCertificateService implements ManagedCertificateService {

    private SecureHttpClient secureHttpClient = new AppClientSecureHttpClient()

    @Override
    List<ManagedCertificate> list() {
        return createRepository().list()
    }

    @Override
    ManagedCertificate get(String id) {
        return createRepository().get(id)
    }

    @Override
    ManagedCertificate create(ManagedCertificate certificate) {
        return createRepository().create(certificate)
    }

    @Override
    ManagedCertificate update(String id, ManagedCertificate certificate) {
        return createRepository().update(id, certificate)
    }

    private Repository<ManagedCertificate> createRepository() {
        return new FastDatastoreRepository<>(ManagedCertificate, secureHttpClient)
    }
}
