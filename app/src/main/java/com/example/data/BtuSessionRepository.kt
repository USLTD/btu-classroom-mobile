package com.example.data

import com.example.model.AuthDiagnosticState
import com.example.model.DiagnosticLogEntry
import com.example.model.ExtensionInstallStatus
import com.example.model.GeckoInitStatus
import com.example.model.GoogleLoginStatus
import com.example.model.LogLevel
import com.example.model.VerificationStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory session and diagnostic state repository.
 * Sensitive session identifiers are kept strictly private in memory and never exposed to UI or logs.
 */
class BtuSessionRepository {

  private val _state = MutableStateFlow(AuthDiagnosticState())
  val state: StateFlow<AuthDiagnosticState> = _state.asStateFlow()

  @Volatile
  private var inMemorySessionCookie: String? = null

  private val logCounter = AtomicLong(0L)
  private val timeFormatter = ThreadLocal.withInitial {
    SimpleDateFormat("HH:mm:ss", Locale.US)
  }

  fun currentSessionValue(): String? = inMemorySessionCookie

  fun appendLog(message: String, level: LogLevel = LogLevel.INFO) {
    val safeMessage = UrlSanitizer.redactSensitiveTokens(message, inMemorySessionCookie)
    val timestamp = timeFormatter.get()?.format(Date()) ?: "--:--:--"
    val entry = DiagnosticLogEntry(
      id = logCounter.incrementAndGet(),
      timestamp = timestamp,
      message = safeMessage,
      level = level,
    )
    _state.update { current ->
      current.copy(logs = (current.logs + entry).takeLast(200))
    }
  }

  fun clearLogs() {
    _state.update { it.copy(logs = emptyList()) }
  }

  fun setGeckoStatus(status: GeckoInitStatus, errorMessage: String? = null) {
    _state.update {
      it.copy(
        geckoInitStatus = status,
        errorMessage = errorMessage ?: it.errorMessage,
      )
    }
  }

  fun setExtensionStatus(status: ExtensionInstallStatus, errorMessage: String? = null) {
    _state.update {
      it.copy(
        extensionInstallStatus = status,
        errorMessage = errorMessage ?: it.errorMessage,
      )
    }
  }

  fun onAnonymousSessionDetected(sessionValue: String?) {
    if (!sessionValue.isNullOrBlank() && inMemorySessionCookie.isNullOrBlank()) {
      inMemorySessionCookie = sessionValue
    }
    val wasDetected = _state.value.anonymousSessionDetected
    _state.update {
      it.copy(anonymousSessionDetected = true)
    }
    if (!wasDetected) {
      appendLog("Anonymous session detected", LogLevel.INFO)
    }
  }

  fun onGoogleLoginStatus(
    status: GoogleLoginStatus,
    stageLabel: String? = null,
    errorMessage: String? = null,
    markCallbackDetected: Boolean = false,
  ) {
    _state.update { current ->
      current.copy(
        googleLoginStatus = status,
        browserStageLabel = stageLabel ?: status.label,
        btuCallbackDetected = current.btuCallbackDetected || markCallbackDetected,
        errorMessage = errorMessage ?: if (status == GoogleLoginStatus.FAILED || status == GoogleLoginStatus.REJECTED_DISALLOWED_USERAGENT) {
          current.errorMessage
        } else {
          null
        },
      )
    }
  }

  fun onSessionReady(sessionValue: String) {
    if (sessionValue.isBlank()) {
      onSessionCookieMissing()
      return
    }
    inMemorySessionCookie = sessionValue
    val alreadyCaptured = _state.value.sessionCaptured
    _state.update {
      it.copy(
        anonymousSessionDetected = true,
        sessionCaptured = true,
        errorMessage = null,
      )
    }
    if (!alreadyCaptured) {
      appendLog("Session acquired", LogLevel.SUCCESS)
    } else {
      appendLog("Session refreshed from GeckoView cookie store", LogLevel.INFO)
    }
  }

  fun onSessionCookieMissing() {
    inMemorySessionCookie = null
    val msg = "Authenticated BTU page was reached, but argus_session cookie was missing in GeckoView."
    _state.update {
      it.copy(
        sessionCaptured = false,
        errorMessage = msg,
      )
    }
    appendLog(msg, LogLevel.ERROR)
  }

  fun setBrowserModalVisible(visible: Boolean) {
    _state.update { it.copy(isBrowserModalVisible = visible) }
  }

  fun setActiveBrowserSession(active: Boolean) {
    _state.update {
      it.copy(
        hasActiveBrowserSession = active,
        browserCanGoBack = if (active) it.browserCanGoBack else false,
        browserLoadProgress = if (active) it.browserLoadProgress else 0,
      )
    }
  }

  fun setBrowserNavigationUi(canGoBack: Boolean? = null, progress: Int? = null) {
    _state.update {
      it.copy(
        browserCanGoBack = canGoBack ?: it.browserCanGoBack,
        browserLoadProgress = progress ?: it.browserLoadProgress,
      )
    }
  }

  fun setVerifying(verifying: Boolean) {
    _state.update {
      it.copy(
        isBusy = verifying,
        verificationStatus = if (verifying) VerificationStatus.VERIFYING else it.verificationStatus,
      )
    }
  }

  fun onVerificationResult(
    status: VerificationStatus,
    httpStatusCode: Int?,
    errorMessage: String?,
  ) {
    _state.update {
      it.copy(
        isBusy = false,
        verificationStatus = status,
        lastHttpStatusCode = httpStatusCode,
        errorMessage = errorMessage,
      )
    }
  }

  fun setError(message: String?) {
    _state.update { it.copy(isBusy = false, errorMessage = message) }
  }

  fun setLogoutDialogVisible(visible: Boolean) {
    _state.update { it.copy(isLogoutDialogVisible = visible) }
  }

  fun resetSessionState(keepLogs: Boolean = true) {
    inMemorySessionCookie = null
    _state.update { current ->
      AuthDiagnosticState(
        geckoInitStatus = current.geckoInitStatus,
        extensionInstallStatus = current.extensionInstallStatus,
        logs = if (keepLogs) current.logs else emptyList(),
      )
    }
  }
}
