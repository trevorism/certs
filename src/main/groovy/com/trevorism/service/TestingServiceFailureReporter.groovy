package com.trevorism.service

import com.trevorism.TestErrorClient
import com.trevorism.https.AppClientSecureHttpClient
import com.trevorism.model.TestError
import jakarta.inject.Singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Singleton
class TestingServiceFailureReporter implements FailureReporter {

    static final String SOURCE = "certs"

    private static final Logger log = LoggerFactory.getLogger(TestingServiceFailureReporter)

    private TestErrorClient testErrorClient = new TestErrorClient(new AppClientSecureHttpClient())

    @Override
    void report(String message, Map details) {
        try {
            testErrorClient.addTestError(new TestError(source: SOURCE, message: message, details: asStrings(details)))
            log.info("Reported to the error ledger: ${message}")
        } catch (Exception e) {
            log.error("Unable to reach the error ledger while reporting: ${message}", e)
        }
    }

    static Map<String, String> asStrings(Map details) {
        return (details ?: [:]).collectEntries { key, value -> [(String.valueOf(key)): String.valueOf(value)] }
    }
}
