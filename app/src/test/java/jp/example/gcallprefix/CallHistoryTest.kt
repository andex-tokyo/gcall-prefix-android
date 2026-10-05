package jp.example.gcallprefix

import org.junit.Assert.*
import org.junit.Test

class CallHistoryTest {
    private fun entry(index: Int) = CallHistoryEntry(index.toLong(), "1840901234${index.toString().padStart(4, '0')}",
        "18400630901234${index.toString().padStart(4, '0')}", CallResult.REDIRECTED)

    @Test fun keepsOnlyTwentyLatestWithoutChangingNumbers() {
        val records = (0..24).fold(emptyList<CallHistoryEntry>()) { history, index ->
            CallHistory.prepend(history, entry(index))
        }
        assertEquals(20, records.size)
        assertEquals((24 downTo 5).map(::entry), records)
    }

    @Test fun arrivalOrderSurvivesClockGoingBackwards() {
        val older = entry(100)
        val newer = entry(1)
        assertEquals(listOf(newer, older), CallHistory.prepend(listOf(older), newer))
    }

    @Test fun unchangedCallsHaveNoRedirectedNumber() {
        val unchanged = CallHistoryEntry(123L, "0120-123-456", null, CallResult.EXCLUDED_NUMBER)
        assertEquals(listOf(unchanged), CallHistory.prepend(emptyList(), unchanged))
        assertNull(unchanged.redirectedNumber)
    }

    @Test fun decisionsDistinguishExclusionUnsupportedOffAndRedirected() {
        assertEquals(CallResult.DISABLED, PrefixRule.evaluate("09012345678", false) { false }.result)
        assertEquals(CallResult.EXCLUDED_NUMBER, PrefixRule.evaluate("1840120123456", true) { false }.result)
        assertEquals(CallResult.UNSUPPORTED_NUMBER, PrefixRule.evaluate("0106831234", true) { false }.result)
        assertEquals(CallDecision(CallResult.REDIRECTED, "186006309012345678"),
            PrefixRule.evaluate("18609012345678", true) { false })
    }

    @Test fun emergencyAndFailedChecksHaveDistinctResultsAndNoRedirect() {
        for (input in listOf("09012345678", "18409012345678")) {
            assertEquals(CallDecision(CallResult.EMERGENCY_NUMBER), PrefixRule.evaluate(input, true) { true })
            assertEquals(CallDecision(CallResult.EMERGENCY_CHECK_FAILED), PrefixRule.evaluate(input, true) { null })
            assertEquals(CallDecision(CallResult.EMERGENCY_CHECK_FAILED),
                PrefixRule.evaluate(input, true) { throw IllegalStateException() })
        }
        assertEquals(CallDecision(CallResult.EMERGENCY_NUMBER),
            PrefixRule.evaluate("184119", true) { error("must not query") })
    }
}
