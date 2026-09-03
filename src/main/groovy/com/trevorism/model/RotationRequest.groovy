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

    static String canonicalAcmeServer(String acmeServer) {
        if (!acmeServer?.trim() || PRODUCTION.equalsIgnoreCase(acmeServer.trim())) {
            return PRODUCTION
        }
        if (STAGING.equalsIgnoreCase(acmeServer.trim())) {
            return STAGING
        }
        throw new IllegalArgumentException(
                "Unknown acmeServer '${acmeServer}'; expected ${PRODUCTION} or ${STAGING}")
    }

    static void requireKnownAcmeServer(String acmeServer) {
        canonicalAcmeServer(acmeServer)
    }
}
