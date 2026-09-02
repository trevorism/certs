package com.trevorism.service

import com.trevorism.model.RotationRequest
import com.trevorism.model.RotationRun

interface CertificateRotationService {

    RotationRun rotate(String certificateId, RotationRequest request)
}
