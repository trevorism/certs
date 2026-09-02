package com.trevorism.service

import com.google.gson.Gson
import com.trevorism.http.HttpClient
import com.trevorism.http.JsonHttpClient
import com.trevorism.model.AuthorizedCertificate
import com.trevorism.model.AuthorizedCertificateList
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Singleton
class DefaultAppEngineCertificateClient implements AppEngineCertificateClient {

    static final String BASE_URL = "https://appengine.googleapis.com/v1/apps"

    private static final Logger log = LoggerFactory.getLogger(DefaultAppEngineCertificateClient)

    @Inject
    AccessTokenProvider accessTokenProvider

    private HttpClient httpClient = new JsonHttpClient()
    private Gson gson = new Gson()

    @Override
    AuthorizedCertificate findByDomain(String gcpProject, String wildcard) {
        String response = httpClient.get("${BASE_URL}/${gcpProject}/authorizedCertificates", authHeaders()).getValue()
        AuthorizedCertificateList page = gson.fromJson(response, AuthorizedCertificateList)
        List<AuthorizedCertificate> matches = (page?.certificates ?: []).findAll { it.domainNames == [wildcard] }
        if (matches.size() != 1) {
            throw new IllegalStateException(
                    "Expected exactly one authorized certificate for ${wildcard} in ${gcpProject} but found ${matches.size()}")
        }
        return matches.first()
    }

    @Override
    AuthorizedCertificate describe(String gcpProject, String certificateId) {
        String url = "${BASE_URL}/${gcpProject}/authorizedCertificates/${certificateId}?view=FULL_CERTIFICATE"
        return gson.fromJson(httpClient.get(url, authHeaders()).getValue(), AuthorizedCertificate)
    }

    @Override
    void replaceCertificateMaterial(String gcpProject, String certificateId, String chainPem, String privateKeyPem,
                                    String displayName) {
        String url = "${BASE_URL}/${gcpProject}/authorizedCertificates/${certificateId}" +
                "?updateMask=certificateRawData,displayName"
        String body = gson.toJson([
                displayName       : displayName,
                certificateRawData: [publicCertificate: chainPem, privateKey: privateKeyPem]])
        log.info("Replacing certificate material on ${certificateId} in ${gcpProject} as ${displayName}")
        httpClient.patch(url, body, authHeaders())
    }

    private Map<String, String> authHeaders() {
        return ["Authorization": "Bearer ${accessTokenProvider.getAccessToken()}".toString(),
                "Content-Type" : "application/json"]
    }
}
