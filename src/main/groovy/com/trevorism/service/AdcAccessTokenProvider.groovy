package com.trevorism.service

import com.google.auth.oauth2.GoogleCredentials
import groovy.transform.CompileStatic
import jakarta.inject.Singleton

@CompileStatic
@Singleton
class AdcAccessTokenProvider implements AccessTokenProvider {

    static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform"

    private GoogleCredentials credentials

    @Override
    String getAccessToken() {
        GoogleCredentials resolved = resolveCredentials()
        resolved.refreshIfExpired()
        return resolved.getAccessToken().getTokenValue()
    }

    private synchronized GoogleCredentials resolveCredentials() {
        if (!credentials) {
            credentials = GoogleCredentials.getApplicationDefault().createScoped(CLOUD_PLATFORM_SCOPE)
        }
        return credentials
    }
}
