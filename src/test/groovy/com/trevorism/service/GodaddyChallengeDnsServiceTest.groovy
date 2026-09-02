package com.trevorism.service

import com.trevorism.https.SecureHttpClient
import com.trevorism.model.DnsRecord
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class GodaddyChallengeDnsServiceTest {

    private static final String LABEL = "_acme-challenge.action"
    private static final String DIGEST = "the-expected-digest"

    private List<String> calls = []

    private GodaddyChallengeDnsService serviceReturning(String getResponse) {
        SecureHttpClient client = [
                delete: { String url -> calls << "DELETE ${url}".toString(); "1" },
                post  : { String url, String body -> calls << "POST ${url} ${body}".toString(); "{}" },
                get   : { String url -> calls << "GET ${url}".toString(); getResponse }
        ] as SecureHttpClient

        GodaddyChallengeDnsService service = new GodaddyChallengeDnsService()
        service.@secureHttpClient = client
        return service
    }

    private static String oneRecord(String name = LABEL, String data = DIGEST) {
        return """[{"name":"${name}","type":"TXT","data":"${data}","ttl":600}]"""
    }

    @Test
    void testSetChallengeDeletesBeforeItCreates() {
        serviceReturning(oneRecord()).setChallenge(LABEL, DIGEST)
        assertEquals(3, calls.size())
        assertTrue(calls[0].startsWith("DELETE"))
        assertTrue(calls[1].startsWith("POST"))
        assertTrue(calls[2].startsWith("GET"))
    }

    @Test
    void testSetChallengeSendsTheTtlExplicitly() {
        serviceReturning(oneRecord()).setChallenge(LABEL, DIGEST)
        assertTrue(calls[1].contains("\"ttl\":600"))
    }

    @Test
    void testSetChallengeSendsTheDigestAndLabel() {
        serviceReturning(oneRecord()).setChallenge(LABEL, DIGEST)
        assertTrue(calls[1].contains("\"data\":\"${DIGEST}\""))
        assertTrue(calls[1].contains("\"name\":\"${LABEL}\""))
    }

    @Test
    void testMissingRecordAfterWriteIsRejected() {
        assertThrows(IllegalStateException) { serviceReturning("[]").setChallenge(LABEL, DIGEST) }
    }

    @Test
    void testSeveralRecordsAfterWriteIsRejected() {
        String two = """[{"name":"${LABEL}","data":"${DIGEST}"},{"name":"${LABEL}","data":"other"}]"""
        assertThrows(IllegalStateException) { serviceReturning(two).setChallenge(LABEL, DIGEST) }
    }

    @Test
    void testRecordWrittenAtTheWrongNameIsRejected() {
        String wrongName = oneRecord("_acme-challenge.datastore", DIGEST)
        assertThrows(IllegalStateException) { serviceReturning(wrongName).setChallenge(LABEL, DIGEST) }
    }

    @Test
    void testRecordHoldingTheWrongValueIsRejected() {
        assertThrows(IllegalStateException) { serviceReturning(oneRecord(LABEL, "stale")).setChallenge(LABEL, DIGEST) }
    }

    @Test
    void testClearChallengeIssuesADelete() {
        serviceReturning("[]").clearChallenge(LABEL)
        assertEquals(1, calls.size())
        assertTrue(calls[0].contains("/record/TXT/${LABEL}"))
    }

    @Test
    void testReadChallengeParsesRecords() {
        List<DnsRecord> records = serviceReturning(oneRecord()).readChallenge(LABEL)
        assertEquals(1, records.size())
        assertEquals(DIGEST, records.first().data)
        assertEquals(600, records.first().ttl)
    }

    @Test
    void testReadChallengeHandlesAnEmptyBody() {
        assertEquals([], serviceReturning("null").readChallenge(LABEL))
    }
}
