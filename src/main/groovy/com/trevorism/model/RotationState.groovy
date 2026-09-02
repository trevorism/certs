package com.trevorism.model

enum RotationState {

    REQUESTED,
    PREFLIGHT_OK,
    CHALLENGE_SET,
    PROPAGATED,
    VALIDATED,
    ISSUED,
    FORMAT_VERIFIED,
    UPLOADED,
    COMPLETED,
    SKIPPED,
    FAILED
}
