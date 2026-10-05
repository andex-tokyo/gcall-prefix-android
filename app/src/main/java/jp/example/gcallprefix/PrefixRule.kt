package jp.example.gcallprefix

/** No Android dependency. null means preserve the original URI exactly. */
object PrefixRule {
    const val PREFIX = "0063"
    private val callerIdPrefixes = listOf("184", "186")
    private val excludedNumbers = setOf("110", "118", "119")
    // G-Call FAQ exclusions. 060 is preserved until G-Call support is confirmed.
    private val excludedPrefixes = listOf("0120", "020", "060", "0570", "0800", "0990", PREFIX)
    // Service numbers are not ordinary geographic subscriber numbers.
    private val specialServicePrefixes = listOf("0170", "0180", "0190")
    // E.164 syntax: '+' followed by 2..15 ASCII digits, with no leading zero.
    // This checks format, not country-code allocation or subscriber existence.
    private val internationalE164 = Regex("\\+[1-9][0-9]{1,14}")
    private val mobileOrIp = Regex("0(?:50|70|80|90)[0-9]{8}")
    // Geographic number shapes, not a database of allocated numbers or active subscribers.
    private val geographic = Regex("0(?:1[1-9]|2[2-9]|3[1-9]|4[2-9]|5[2-9]|6[1-9]|7[2-9]|8[2-9]|9[2-9])[0-9]{7}")

    private fun compactNumber(raw: String): String =
        raw.filterNot { it == ' ' || it == '-' || it == '(' || it == ')' }

    fun normalizedNumber(raw: String): String? {
        // Never discard dialable controls, post-dial digits or unknown symbols.
        val compact = compactNumber(raw)
        if (compact.isEmpty()) return null
        return if (compact.startsWith("+81") && compact.drop(3).all { it in '0'..'9' }
            && compact.length > 3 && compact[3] != '0') {
            "0" + compact.drop(3)
        } else compact
    }

    /** Emergency check is injected; unknown (null) or failure always preserves the call. */
    fun redirectedNumber(raw: String, enabled: Boolean, emergencyCheck: (String) -> Boolean?): String? =
        evaluate(raw, enabled, emergencyCheck).redirectedNumber

    fun evaluate(raw: String, enabled: Boolean, emergencyCheck: (String) -> Boolean?): CallDecision {
        if (!enabled) return CallDecision(CallResult.DISABLED)
        val original = compactNumber(raw)
        val callerId = callerIdPrefixes.firstOrNull(original::startsWith).orEmpty()
        // Strip exactly one caller-ID modifier. Repeated/mixed modifiers remain unsupported.
        val destination = original.removePrefix(callerId)
        val number = if (destination.startsWith("+") && !destination.startsWith("+81")) {
            // Foreign numbers must already be strict E.164; do not repair punctuation.
            val unformattedDestination = raw.removePrefix(callerId)
            if (!internationalE164.matches(unformattedDestination))
                return CallDecision(CallResult.UNSUPPORTED_NUMBER)
            destination.drop(1)
        } else {
            val domestic = normalizedNumber(destination) ?: return CallDecision(CallResult.UNSUPPORTED_NUMBER)
            if (domestic in excludedNumbers) return CallDecision(CallResult.EMERGENCY_NUMBER)
            if (excludedPrefixes.any(domestic::startsWith)) return CallDecision(CallResult.EXCLUDED_NUMBER)
            if (specialServicePrefixes.any(domestic::startsWith)) return CallDecision(CallResult.EXCLUDED_NUMBER)
            if (!mobileOrIp.matches(domestic) && !geographic.matches(domestic))
                return CallDecision(CallResult.UNSUPPORTED_NUMBER)
            domestic
        }

        // 184/186 must precede 0063, and emergency checks must also see the bare destination.
        val prefixed = PREFIX + number
        val redirected = callerId + prefixed
        try {
            for (candidate in listOf(original, destination, number, prefixed, redirected).distinct()) {
                when (emergencyCheck(candidate)) {
                    true -> return CallDecision(CallResult.EMERGENCY_NUMBER)
                    null -> return CallDecision(CallResult.EMERGENCY_CHECK_FAILED)
                    false -> Unit
                }
            }
        } catch (_: RuntimeException) {
            return CallDecision(CallResult.EMERGENCY_CHECK_FAILED)
        }
        return CallDecision(CallResult.REDIRECTED, redirected)
    }
}
