package com.trevorism.service

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue

class PlatformFailureReporterTest {

    @Test
    void testNonStringDetailValuesAreCoerced() {
        Map<String, String> coerced = PlatformFailureReporter.asStrings([daysLeft: 9, enabled: true])
        assertEquals("9", coerced.daysLeft)
        assertEquals("true", coerced.enabled)
    }

    @Test
    void testNullDetailValuesSurviveAsText() {
        assertEquals("null", PlatformFailureReporter.asStrings([lastRotatedAt: null]).lastRotatedAt)
    }

    @Test
    void testAbsentDetailsBecomeAnEmptyMap() {
        assertEquals([:], PlatformFailureReporter.asStrings(null))
    }

    @Test
    void testEveryValueIsAString() {
        Map<String, String> coerced = PlatformFailureReporter.asStrings([a: 1, b: "two", c: 3.5])
        assertEquals([String, String, String], coerced.values().collect { it.class })
    }

    @Test
    void testTheAlertBodyLeadsWithTheMessageThenTheDetails() {
        String body = PlatformFailureReporter.buildBody("Certificate rotation failed for *.draw.trevorism.com",
                [wildcard: "*.draw.trevorism.com", reachedState: "PROPAGATED"])
        List<String> lines = body.split("\n").toList()
        assertEquals("Certificate rotation failed for *.draw.trevorism.com", lines.first())
        assertTrue(lines.contains("wildcard: *.draw.trevorism.com"))
        assertTrue(lines.contains("reachedState: PROPAGATED"))
    }

    @Test
    void testAnAlertBodyWithoutDetailsIsStillTheMessage() {
        assertEquals("something broke", PlatformFailureReporter.buildBody("something broke", [:]))
    }
}
