package com.trevorism

import com.google.gson.Gson
import com.trevorism.http.HttpClient
import com.trevorism.http.JsonHttpClient
import com.trevorism.https.AppClientSecureHttpClient
import com.trevorism.https.SecureHttpClient

import java.time.Instant

class CertsWorld {

    static final String BASE_URL = System.getenv("ACCEPTANCE_BASE_URL") ?: "https://certs.project.trevorism.com"
    static final String ZONE = "trevorism.com"
    static final String UNKNOWN_ID = "0"

    private final Gson gson = new Gson()
    private final SecureHttpClient authClient = new AppClientSecureHttpClient()
    private final HttpClient anonClient = new JsonHttpClient()

    String body
    boolean rejected
    List<Map> certificates = []
    List<Map> rotationRuns = []
    Map certificate
    Map rotationRun
    Map verification

    List<Map> listCertificates() {
        body = authClient.get("${BASE_URL}/api/certificate".toString())
        certificates = gson.fromJson(body, List) ?: []
        return certificates
    }

    Map readCertificate(String id) {
        body = authClient.get("${BASE_URL}/api/certificate/${id}".toString())
        certificate = gson.fromJson(body, Map)
        return certificate
    }

    Map verifyCertificate(String id) {
        body = authClient.get("${BASE_URL}/api/certificate/${id}/verify".toString())
        verification = gson.fromJson(body, Map)
        return verification
    }

    List<Map> listRotationRuns() {
        body = authClient.get("${BASE_URL}/api/rotation".toString())
        rotationRuns = gson.fromJson(body, List) ?: []
        return rotationRuns
    }

    Map readRotationRun(String id) {
        body = authClient.get("${BASE_URL}/api/rotation/${id}".toString())
        rotationRun = gson.fromJson(body, Map)
        return rotationRun
    }

    Map firstCertificate() {
        certificate = listCertificates().first()
        return certificate
    }

    Map firstRotationRun() {
        rotationRun = listRotationRuns().first()
        return rotationRun
    }

    static String expectedWildcard(Map certificate) {
        return "*.${certificate.category}.${ZONE}"
    }

    static String expectedChallengeFqdn(Map certificate) {
        return "_acme-challenge.${certificate.category}.${ZONE}"
    }

    static boolean coveredByWildcard(Map certificate) {
        String suffix = ".${certificate.category}.${ZONE}"
        String probeHost = certificate.probeHost ?: ""
        if (!probeHost.endsWith(suffix)) {
            return false
        }
        return !(probeHost - suffix).contains(".")
    }

    static List<Instant> eventTimestamps(Map run) {
        return (run.events ?: []).collect { Instant.parse(it.at as String) }
    }

    Map incompleteRegistration() {
        return [:]
    }

    Map unknownCertificateRotation() {
        return [acmeServer: "staging", minDaysRemaining: 30]
    }

    void anonymously(Closure call) {
        try {
            body = call()
            rejected = false
        }
        catch (Exception ignored) {
            rejected = true
            body = null
        }
    }

    void anonGet(String path) {
        anonymously { anonClient.get("${BASE_URL}/${path}".toString()) }
    }

    void anonPost(String path, Map payload) {
        anonymously { anonClient.post("${BASE_URL}/${path}".toString(), gson.toJson(payload)) }
    }
}
