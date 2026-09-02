package com.trevorism.model

class RotationRequest {

    static final String PRODUCTION = "production"
    static final String STAGING = "staging"

    String acmeServer = PRODUCTION
    boolean force = false
    int minDaysRemaining = 30

    boolean isStaging() {
        return STAGING.equalsIgnoreCase(acmeServer)
    }
}
