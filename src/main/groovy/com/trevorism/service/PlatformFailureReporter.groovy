package com.trevorism.service

import com.trevorism.AlertClient
import com.trevorism.TestErrorClient
import com.trevorism.https.AppClientSecureHttpClient
import com.trevorism.model.Alert
import com.trevorism.model.TestError
import jakarta.inject.Singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Singleton
class PlatformFailureReporter implements FailureReporter {

    static final String SOURCE = "certs"

    private static final Logger log = LoggerFactory.getLogger(PlatformFailureReporter)

    private TestErrorClient testErrorClient = new TestErrorClient(new AppClientSecureHttpClient())
    private AlertClient alertClient = new AlertClient(new AppClientSecureHttpClient())

    @Override
    void report(String message, Map details) {
        Map<String, String> coerced = asStrings(details)
        recordInLedger(message, coerced)
        raiseAlert(message, coerced)
    }

    private void recordInLedger(String message, Map<String, String> details) {
        try {
            testErrorClient.addTestError(new TestError(source: SOURCE, message: message, details: details))
        } catch (Exception e) {
            log.error("Unable to record in the error ledger: ${message}", e)
        }
    }

    private void raiseAlert(String message, Map<String, String> details) {
        try {
            alertClient.sendAlert(new Alert(subject: "certs: ${message}", body: buildBody(message, details)))
        } catch (Exception e) {
            log.error("Unable to raise an alert for: ${message}", e)
        }
    }

    static String buildBody(String message, Map<String, String> details) {
        return ([message] + details.collect { key, value -> "${key}: ${value}".toString() }).join("\n")
    }

    static Map<String, String> asStrings(Map details) {
        return (details ?: [:]).collectEntries { key, value -> [(String.valueOf(key)): String.valueOf(value)] }
    }
}
