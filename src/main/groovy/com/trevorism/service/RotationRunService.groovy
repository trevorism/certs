package com.trevorism.service

import com.trevorism.model.RotationRun

interface RotationRunService {

    List<RotationRun> list()

    RotationRun get(String id)

    RotationRun create(RotationRun run)

    RotationRun update(String id, RotationRun run)
}
