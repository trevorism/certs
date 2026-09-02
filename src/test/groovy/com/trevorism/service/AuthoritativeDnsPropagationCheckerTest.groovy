package com.trevorism.service

import org.junit.jupiter.api.Test
import org.xbill.DNS.CNAMERecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Section
import org.xbill.DNS.TXTRecord

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

class AuthoritativeDnsPropagationCheckerTest {

    private static final String FQDN = "_acme-challenge.action.trevorism.com"
    private static final String DIGEST = "the-expected-digest"
    private static final List<String> NAMESERVERS = ["1.1.1.1", "2.2.2.2"]

    private static AuthoritativeDnsPropagationChecker checkerServing(Map<String, List<String>> byNameserver) {
        AuthoritativeDnsPropagationChecker checker = new AuthoritativeDnsPropagationChecker() {
            @Override
            List<String> resolveAuthoritativeNameserverAddresses(String zone) {
                return NAMESERVERS
            }

            @Override
            List<String> queryTxtValues(String nameserverAddress, String fqdn) {
                if (byNameserver[nameserverAddress] == null) {
                    throw new IOException("unreachable")
                }
                return byNameserver[nameserverAddress]
            }
        }
        checker.settleMillis = 0
        checker.pollIntervalMillis = 1
        checker.timeoutMillis = 500
        checker.requiredCleanRounds = 2
        return checker
    }

    @Test
    void testRoundIsCleanWhenEveryNameserverServesTheValue() {
        def checker = checkerServing(["1.1.1.1": [DIGEST], "2.2.2.2": [DIGEST]])
        assertTrue(checker.isRoundClean(NAMESERVERS, FQDN, DIGEST))
    }

    @Test
    void testRoundIsDirtyWhenOneNameserverLagsBehind() {
        def checker = checkerServing(["1.1.1.1": [DIGEST], "2.2.2.2": []])
        assertFalse(checker.isRoundClean(NAMESERVERS, FQDN, DIGEST))
    }

    @Test
    void testRoundIsDirtyWhenAStaleValueIsStillServed() {
        def checker = checkerServing(["1.1.1.1": [DIGEST], "2.2.2.2": ["a-previous-digest"]])
        assertFalse(checker.isRoundClean(NAMESERVERS, FQDN, DIGEST))
    }

    @Test
    void testAnUnreachableNameserverIsNotClean() {
        def checker = checkerServing(["1.1.1.1": [DIGEST]])
        assertFalse(checker.servesExpectedValue("2.2.2.2", FQDN, DIGEST))
    }

    @Test
    void testAwaitPropagationReturnsOnceEveryNameserverAgrees() {
        def checker = checkerServing(["1.1.1.1": [DIGEST], "2.2.2.2": [DIGEST]])
        checker.awaitPropagation(FQDN, DIGEST)
    }

    @Test
    void testAwaitPropagationTimesOutWhenTheValueNeverAppears() {
        def checker = checkerServing(["1.1.1.1": [DIGEST], "2.2.2.2": []])
        assertThrows(IllegalStateException) { checker.awaitPropagation(FQDN, DIGEST) }
    }

    @Test
    void testAwaitPropagationFailsWhenNoNameserverCanBeResolved() {
        AuthoritativeDnsPropagationChecker checker = new AuthoritativeDnsPropagationChecker() {
            @Override
            List<String> resolveAuthoritativeNameserverAddresses(String zone) {
                return []
            }
        }
        assertThrows(IllegalStateException) { checker.awaitPropagation(FQDN, DIGEST) }
    }

    @Test
    void testATxtAnswerIsRead() {
        Message message = new Message()
        message.addRecord(new TXTRecord(Name.fromString("${FQDN}."), DClass.IN, 600, DIGEST), Section.ANSWER)
        assertEquals([DIGEST], AuthoritativeDnsPropagationChecker.readTxtValues(message))
    }

    @Test
    void testAWildcardCnameAnswerIsNotMistakenForTheChallenge() {
        Message message = new Message()
        message.addRecord(new CNAMERecord(Name.fromString("${FQDN}."), DClass.IN, 3600,
                Name.fromString("ghs.googlehosted.com.")), Section.ANSWER)
        assertEquals([], AuthoritativeDnsPropagationChecker.readTxtValues(message))
    }

    @Test
    void testAnEmptyAnswerSectionYieldsNothing() {
        assertEquals([], AuthoritativeDnsPropagationChecker.readTxtValues(new Message()))
    }
}
