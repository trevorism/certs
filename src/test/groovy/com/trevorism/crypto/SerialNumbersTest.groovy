package com.trevorism.crypto

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assertions.assertTrue

class SerialNumbersTest {

    @Test
    void testOpensslPaddingAndBigIntegerFormAreTheSameSerial() {
        assertTrue(SerialNumbers.same("6d9820944fa2d75ffa1b574d1b1ca32ec49",
                "06D9820944FA2D75FFA1B574D1B1CA32EC49"))
    }

    @Test
    void testCaseIsIgnored() {
        assertTrue(SerialNumbers.same("054111F02FCA3516B4FF23024486B7558BF3",
                "054111f02fca3516b4ff23024486b7558bf3"))
    }

    @Test
    void testColonSeparatedFormIsAccepted() {
        assertTrue(SerialNumbers.same("0a:1b:2c", "A1B2C"))
    }

    @Test
    void testDifferentSerialsDoNotMatch() {
        assertFalse(SerialNumbers.same("6d9820944fa2d75ffa1b574d1b1ca32ec49",
                "054111f02fca3516b4ff23024486b7558bf3"))
    }

    @Test
    void testNullsNeverMatch() {
        assertFalse(SerialNumbers.same(null, "abc"))
        assertFalse(SerialNumbers.same("abc", null))
        assertFalse(SerialNumbers.same(null, null))
    }

    @Test
    void testNormalizeStripsLeadingZeros() {
        assertEquals("a1b2", SerialNumbers.normalize("000A1B2"))
    }

    @Test
    void testNormalizeOfNothingIsNull() {
        assertNull(SerialNumbers.normalize(null))
        assertNull(SerialNumbers.normalize(""))
    }

    @Test
    void testAllZeroSerialCollapsesToZeroRatherThanEmpty() {
        assertEquals("0", SerialNumbers.normalize("0000"))
    }
}
