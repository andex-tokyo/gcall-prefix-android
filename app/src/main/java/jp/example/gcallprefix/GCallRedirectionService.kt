package jp.example.gcallprefix

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.telecom.CallRedirectionService
import android.telecom.PhoneAccountHandle
import android.telephony.TelephonyManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class GCallRedirectionService : CallRedirectionService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var responded = false
    private var decisionJob: Job? = null
    private var originalNumber = ""
    private var requestTime = 0L
    private val fallback = Runnable {
        if (!responded) {
            decisionJob?.cancel()
            respond(CallDecision(CallResult.TIMEOUT), null)
        }
    }

    override fun onPlaceCall(handle: Uri, initialPhoneAccount: PhoneAccountHandle, allowInteractiveResponse: Boolean) {
        // Telecom supplies a single call per binding. Respond once, including fallback races.
        responded = false
        originalNumber = handle.schemeSpecificPart
        requestTime = System.currentTimeMillis()
        handler.postDelayed(fallback, 2500L)
        decisionJob = scope.launch {
            val decision = try {
                withTimeoutOrNull(1500L) {
                    withContext(Dispatchers.IO) {
                        if (handle.scheme != "tel" || handle.fragment != null)
                            return@withContext CallDecision(CallResult.UNSUPPORTED_NUMBER)
                        val enabled = SettingsRepository(applicationContext).enabled.first()
                        val telephony = getSystemService(TelephonyManager::class.java)
                        PrefixRule.evaluate(handle.schemeSpecificPart, enabled) { number ->
                            telephony?.isEmergencyNumber(number)
                        }
                    }
                } ?: CallDecision(CallResult.TIMEOUT)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                CallDecision(CallResult.SETTINGS_FAILED)
            }
            respond(decision, initialPhoneAccount)
        }
    }

    private fun respond(decision: CallDecision, account: PhoneAccountHandle?) {
        if (responded) return
        responded = true
        handler.removeCallbacks(fallback)
        val completed = try {
            if (decision.redirectedNumber != null && account != null) {
                redirectCall(Uri.fromParts("tel", decision.redirectedNumber, null), account, false)
            } else placeCallUnmodified()
            decision
        } catch (_: Exception) {
            // A failed Binder response has unknown delivery status. Never respond a second time.
            CallDecision(CallResult.RESPONSE_FAILED)
        }
        record(completed)
    }

    private fun record(decision: CallDecision) {
        HistoryRepository.recordAfterResponse(applicationContext,
            CallHistoryEntry(requestTime, originalNumber, decision.redirectedNumber, decision.result))
    }

    override fun onRedirectionTimeout() {
        val needsRecord = !responded
        responded = true
        handler.removeCallbacks(fallback)
        decisionJob?.cancel()
        if (needsRecord) record(CallDecision(CallResult.FRAMEWORK_TIMEOUT))
    }

    override fun onDestroy() {
        handler.removeCallbacks(fallback)
        scope.cancel()
        super.onDestroy()
    }
}
