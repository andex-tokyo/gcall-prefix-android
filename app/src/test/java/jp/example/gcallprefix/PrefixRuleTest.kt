package jp.example.gcallprefix

import org.junit.Assert.*
import org.junit.Test

class PrefixRuleTest {
    private fun result(number: String, enabled: Boolean = true) =
        PrefixRule.redirectedNumber(number, enabled) { false } ?: number

    @Test fun requestedCases() {
        mapOf(
            "09012345678" to "006309012345678", "0312345678" to "00630312345678",
            "0120123456" to "0120123456", "08001234567" to "08001234567",
            "0570123456" to "0570123456", "110" to "110", "118" to "118", "119" to "119",
            "#1234" to "#1234", "*1234" to "*1234", "006309012345678" to "006309012345678"
        ).forEach { (input, expected) -> assertEquals(input, expected, result(input)) }
    }
    @Test fun disabledNeverChecksOrRedirects() {
        assertNull(PrefixRule.redirectedNumber("09012345678", false) { error("must not check") })
    }
    @Test fun androidEmergencyNeverRedirects() {
        assertNull(PrefixRule.redirectedNumber("09012345678", true) { true })
    }
    @Test fun unknownAndFailedEmergencyChecksPreserveCall() {
        assertNull(PrefixRule.redirectedNumber("09012345678", true) { null })
        assertNull(PrefixRule.redirectedNumber("09012345678", true) { throw IllegalStateException() })
    }
    @Test fun checkOriginalNormalizedAndDestination() {
        val checked = mutableListOf<String>()
        assertEquals("006309012345678", PrefixRule.redirectedNumber("+819012345678", true) { checked += it; false })
        assertEquals(listOf("+819012345678", "09012345678", "006309012345678"), checked)
        for (emergency in checked) {
            assertNull(PrefixRule.redirectedNumber("+819012345678", true) { it == emergency })
        }
    }
    @Test fun formattedDomesticNumbers() {
        assertEquals("006309012345678", result("090-1234-5678"))
        assertEquals("00630312345678", result("(03) 1234-5678"))
        assertEquals("+81120123456", result("+81120123456"))
    }
    @Test fun unsupportedNumbersPreserveOriginal() {
        listOf("", "112", "911", "12345", "+12025550123", "09012345678,123", "09012345678;123",
            "09012345678#", "*3109012345678", "０９０１２３４５６７８", "+8109012345678",
            "09012abc678", "090123456789", "tel:09012345678").forEach { assertEquals(it, result(it)) }
    }
    @Test fun exclusionsPreserveFormattingAndDoNotQueryAndroid() {
        listOf("110", "118", "119", "0120-123-456", "+818001234567", "+81570123456", "006309012345678").forEach {
            assertNull(PrefixRule.redirectedNumber(it, true) { error("excluded") })
        }
    }
    @Test fun repeatedApplicationIsIdempotent() {
        val once = result("09012345678")
        assertEquals(once, result(once))
    }
    @Test fun nonSupportedPrefixesWithCallerIdAndCountryCodeArePreserved() {
        listOf("02012345678", "06012345678", "0990123456", "0120123456", "08001234567", "0570123456",
            "0170123456", "0180123456", "0190123456").forEach { number ->
            for (callerId in listOf("", "184", "186")) {
                for (destination in listOf(number, "+81" + number.drop(1))) {
                    val input = callerId + destination
                    assertNull(input, PrefixRule.redirectedNumber(input, true) { error("must not query") })
                }
            }
        }
    }

    @Test fun ordinaryDomesticNumberShapes() {
        listOf("0111234567", "0221234567", "0312345678", "0421234567", "0521234567", "0612345678",
            "0721234567", "0821234567", "0921234567", "05012345678", "07012345678", "08012345678",
            "09012345678").forEach { number ->
            assertEquals(number, "0063" + number, result(number))
            assertEquals(number, "0063" + number, result("+81" + number.drop(1)))
        }
    }

    @Test fun internationalCarrierAndInvalidLengthsAreNotDomestic() {
        listOf("0001234567", "0012345678", "0101234567", "01012345678", "0106831234", "00360312345678",
            "006751312345678", "0211234567", "0401234567", "0711234567", "0911234567", "0301234567",
            "0501234567", "0701234567", "0801234567", "0901234567", "03123456789", "06123456789",
            "090123456789", "+8109012345678", "+12025550123").forEach { number ->
            for (callerId in listOf("", "184", "186")) {
                val input = callerId + number
                assertNull(input, PrefixRule.redirectedNumber(input, true) { error("must not query") })
            }
        }
    }

    @Test fun callerIdComesBeforeGCallPrefix() {
        for (callerId in listOf("184", "186")) {
            for (number in listOf("09012345678", "0312345678", "05012345678")) {
                val expected = callerId + "0063" + number
                assertEquals(expected, result(callerId + number))
                assertEquals(expected, result(callerId + "+81" + number.drop(1)))
                assertEquals(expected, result(callerId + "-" + number))
                assertEquals(expected, result(expected))
                assertEquals(callerId + number, result(callerId + number, enabled = false))
            }
        }
    }

    @Test fun callerIdDoesNotHideEmergencyOrExcludedNumbers() {
        for (callerId in listOf("184", "186")) {
            listOf("110", "118", "119", "112", "911", "#1234", "*1234", "006309012345678").forEach { number ->
                val input = callerId + number
                assertNull(input, PrefixRule.redirectedNumber(input, true) { error("must not query") })
            }
        }
    }

    @Test fun callerIdEmergencyChecksCoverEveryRepresentation() {
        for (callerId in listOf("184", "186")) {
            val checked = mutableListOf<String>()
            val original = callerId + "+819012345678"
            val expected = callerId + "006309012345678"
            assertEquals(expected, PrefixRule.redirectedNumber(original, true) { checked += it; false })
            assertEquals(listOf(original, "+819012345678", "09012345678", "006309012345678", expected), checked)
            for (emergency in checked) {
                assertNull(PrefixRule.redirectedNumber(original, true) { it == emergency })
                assertNull(PrefixRule.redirectedNumber(original, true) { if (it == emergency) null else false })
                assertNull(PrefixRule.redirectedNumber(original, true) {
                    if (it == emergency) throw IllegalStateException() else false
                })
            }
        }
    }

    @Test fun repeatedCallerIdModifiersAndPostDialRemainUnmodified() {
        listOf("184", "186", "18418409012345678", "18418609012345678", "18618409012345678",
            "18618609012345678", "18409012345678,123", "18609012345678;123", "18409012345678#").forEach {
            assertEquals(it, result(it))
        }
    }

}
