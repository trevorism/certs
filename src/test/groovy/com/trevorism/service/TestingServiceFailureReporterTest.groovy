package com.trevorism.service

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals

class TestingServiceFailureReporterTest {

    @Test
    void testNonStringDetailValuesAreCoerced() {
        Map<String, String> coerced = TestingServiceFailureReporter.asStrings([daysLeft: 9, enabled: true])
        assertEquals("9", coerced.daysLeft)
        assertEquals("true", coerced.enabled)
    }

    @Test
    void testNullDetailValuesSurviveAsText() {
        assertEquals("null", TestingServiceFailureReporter.asStrings([lastRotatedAt: null]).lastRotatedAt)
    }

    @Test
    void testAbsentDetailsBecomeAnEmptyMap() {
        assertEquals([:], TestingServiceFailureReporter.asStrings(null))
    }

    @Test
    void testEveryValueIsAString() {
        Map<String, String> coerced = TestingServiceFailureReporter.asStrings([a: 1, b: "two", c: 3.5])
        assertEquals(["1", "two", "3.5"], coerced.values().toList())
        assertEquals([String, String, String], coerced.values().collect { it.class })
    }
}
