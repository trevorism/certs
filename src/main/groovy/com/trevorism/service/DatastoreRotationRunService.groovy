package com.trevorism.service

import com.trevorism.data.FastDatastoreRepository
import com.trevorism.data.Repository
import com.trevorism.https.AppClientSecureHttpClient
import com.trevorism.https.SecureHttpClient
import com.trevorism.model.RotationRun
import jakarta.inject.Singleton

@Singleton
class DatastoreRotationRunService implements RotationRunService {

    private SecureHttpClient secureHttpClient = new AppClientSecureHttpClient()

    @Override
    List<RotationRun> list() {
        return createRepository().list()
    }

    @Override
    RotationRun get(String id) {
        return createRepository().get(id)
    }

    @Override
    RotationRun create(RotationRun run) {
        return createRepository().create(run)
    }

    @Override
    RotationRun update(String id, RotationRun run) {
        return createRepository().update(id, run)
    }

    private Repository<RotationRun> createRepository() {
        return new FastDatastoreRepository<>(RotationRun, secureHttpClient)
    }
}
