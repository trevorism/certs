package com.trevorism.service

import com.trevorism.data.FastDatastoreRepository
import com.trevorism.data.Repository
import com.trevorism.https.AppClientSecureHttpClient
import com.trevorism.https.SecureHttpClient
import com.trevorism.model.AcmeAccountRecord
import jakarta.inject.Singleton

@Singleton
class DatastoreAcmeAccountStore implements AcmeAccountStore {

    private SecureHttpClient secureHttpClient = new AppClientSecureHttpClient()

    @Override
    AcmeAccountRecord load(String server) {
        return createRepository().list().find { it.server == server }
    }

    @Override
    AcmeAccountRecord store(AcmeAccountRecord record) {
        return createRepository().create(record)
    }

    private Repository<AcmeAccountRecord> createRepository() {
        return new FastDatastoreRepository<>(AcmeAccountRecord, secureHttpClient)
    }
}
