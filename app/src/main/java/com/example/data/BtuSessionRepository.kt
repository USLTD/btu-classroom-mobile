package com.example.data

import com.example.model.AuthDiagnosticState
import com.example.model.CookieMetadata
import com.example.model.DiagnosticLogEntry
import com.example.model.ExtensionInstallStatus
import com.example.model.GeckoInitStatus
import com.example.model.GoogleLoginStatus
import com.example.model.LogLevel
import com.example.model.SessionExpiryStatus
import com.example.model.UserAgentPreset
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
 * In-memory session, OAuth capture, cookie metadata, and diagnostic state repository.
 * Sensitive session identifiers and OAuth tokens/codes are kept strictly private in memory and never exposed to UI or logs.
 */
class BtuSessionRepository {

  private val _state = MutableStateFlow(AuthDiagnosticState())
  val state: StateFlow<AuthDiagnosticState> = _state.asStateFlow()

  @Volatile
  private var inMemorySessionCookie: String? = null

  @Volatile
  private var lastKnownValidSessionCookie: String? = null

  @Volatile
  private var inMemoryOAuthTokenPayload: String? = null

  private val logCounter = AtomicLong(0L)
  private val timeFormatter = ThreadLocal.withInitial {
    SimpleDateFormat("HH:mm:ss", Locale.US)
  }

  fun currentSessionValue(): String? = inMemorySessionCookie

  fun lastKnownValidSessionValue(): String? = lastKnownValidSessionCookie ?: inMemorySessionCookie

  fun appendLog(message: String, level: LogLevel = LogLevel.INFO) {
    var safeMessage = UrlSanitizer.redactSensitiveTokens(message, inMemorySessionCookie)
    lastKnownValidSessionCookie?.let { prev ->
      safeMessage = UrlSanitizer.redactSensitiveTokens(safeMessage, prev)
    }
    inMemoryOAuthTokenPayload?.let { token ->
      safeMessage = UrlSanitizer.redactSensitiveTokens(safeMessage, token)
    }
    val timestamp = timeFormatter.get()?.format(Date()) ?: "--:--:--"
    val entry = DiagnosticLogEntry(
      id = logCounter.incrementAndGet(),
      timestamp = timestamp,
      message = safeMessage,
      level = level,
    )
    _state.update { current ->
      current.copy(logs = (current.logs + entry).takeLast(250))
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

  fun onAnonymousSessionDetected(sessionValue: String?, expirationDateEpochSeconds: Long? = null) {
    if (!sessionValue.isNullOrBlank() && inMemorySessionCookie.isNullOrBlank()) {
      inMemorySessionCookie = sessionValue
    }
    val wasDetected = _state.value.anonymousSessionDetected
    _state.update {
      it.copy(
        anonymousSessionDetected = true,
        sessionExpiresAtEpochSeconds = expirationDateEpochSeconds ?: it.sessionExpiresAtEpochSeconds,
        sessionExpiryStatus = if (it.sessionCaptured) it.sessionExpiryStatus else SessionExpiryStatus.ACTIVE,
      )
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

  fun onOAuthTokenCaptured(
    hasAuthorizationCode: Boolean,
    hasAccessToken: Boolean,
    tokenPayload: String?,
    oauthError: String?,
  ) {
    if (!tokenPayload.isNullOrBlank()) {
      inMemoryOAuthTokenPayload = tokenPayload
    }
    if (!oauthError.isNullOrBlank()) {
      val safeError = UrlSanitizer.redactSensitiveTokens(oauthError)
      _state.update {
        it.copy(
          oauthTokenCaptured = false,
          errorMessage = "OAuth callback returned error: $safeError",
        )
      }
      appendLog("OAuth callback returned error ($safeError)", LogLevel.ERROR)
      return
    }
    if (hasAuthorizationCode || hasAccessToken) {
      val kind = when {
        hasAuthorizationCode && hasAccessToken -> "authorization code + access token"
        hasAuthorizationCode -> "authorization code"
        else -> "access token"
      }
      val alreadyCaptured = _state.value.oauthTokenCaptured
      _state.update { it.copy(oauthTokenCaptured = true) }
      if (!alreadyCaptured) {
        appendLog("WebExtension observed OAuth $kind on BTU callback (stored privately in memory)", LogLevel.INFO)
      }
    }
  }

  fun onSessionReady(sessionValue: String, expirationDateEpochSeconds: Long? = null) {
    if (sessionValue.isBlank()) {
      onSessionCookieMissing()
      return
    }
    inMemorySessionCookie = sessionValue
    lastKnownValidSessionCookie = sessionValue
    val alreadyCaptured = _state.value.sessionCaptured
    _state.update {
      it.copy(
        anonymousSessionDetected = true,
        sessionCaptured = true,
        sessionExpiryStatus = SessionExpiryStatus.ACTIVE,
        sessionExpiresAtEpochSeconds = expirationDateEpochSeconds ?: it.sessionExpiresAtEpochSeconds,
        isReauthPromptVisible = false,
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

  fun setSessionRefreshing() {
    _state.update {
      it.copy(
        sessionExpiryStatus = SessionExpiryStatus.REFRESHING,
      )
    }
  }

  fun markSessionExpiredRequiresReauth(reason: String) {
    inMemorySessionCookie = null
    val msg = "argus_session cookie expired ($reason) and automatic refresh could not restore it. Please re-authenticate."
    _state.update {
      it.copy(
        sessionCaptured = false,
        sessionExpiryStatus = SessionExpiryStatus.EXPIRED_REAUTH_REQUIRED,
        isReauthPromptVisible = true,
        verificationStatus = VerificationStatus.UNAUTHENTICATED,
        errorMessage = msg,
      )
    }
    appendLog(msg, LogLevel.ERROR)
  }

  fun dismissReauthPrompt() {
    _state.update { it.copy(isReauthPromptVisible = false) }
  }

  fun setUserAgentSelection(preset: UserAgentPreset, customInput: String? = null) {
    _state.update {
      it.copy(
        selectedUserAgentPreset = preset,
        customUserAgentInput = customInput ?: it.customUserAgentInput,
      )
    }
  }

  fun onUserAgentApplied(modeId: String, hasOverride: Boolean) {
    val preset = UserAgentPreset.entries.firstOrNull { it.id == modeId } ?: UserAgentPreset.DEFAULT
    val label = if (!hasOverride || preset == UserAgentPreset.DEFAULT) {
      UserAgentPreset.DEFAULT.label
    } else {
      preset.label
    }
    _state.update {
      it.copy(
        selectedUserAgentPreset = preset,
        appliedUserAgentLabel = label,
      )
    }
    appendLog("WebExtension User-Agent updated: $label", LogLevel.INFO)
  }

  fun setCookieDomainInput(domain: String) {
    _state.update { it.copy(cookieDomainInput = domain) }
  }

  fun onDomainCookiesListed(cookies: List<CookieMetadata>) {
    _state.update { it.copy(inspectedCookies = cookies) }
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
    if (status == VerificationStatus.AUTHENTICATED && !inMemorySessionCookie.isNullOrBlank()) {
      lastKnownValidSessionCookie = inMemorySessionCookie
    }
    _state.update {
      it.copy(
        isBusy = false,
        verificationStatus = status,
        lastHttpStatusCode = httpStatusCode,
        errorMessage = errorMessage,
        sessionExpiryStatus = when {
          status == VerificationStatus.AUTHENTICATED -> SessionExpiryStatus.ACTIVE
          status == VerificationStatus.UNAUTHENTICATED && it.sessionExpiryStatus == SessionExpiryStatus.REFRESHING ->
            SessionExpiryStatus.EXPIRED_REAUTH_REQUIRED
          else -> it.sessionExpiryStatus
        },
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
    lastKnownValidSessionCookie = null
    inMemoryOAuthTokenPayload = null
    _state.update { current ->
      AuthDiagnosticState(
        geckoInitStatus = current.geckoInitStatus,
        extensionInstallStatus = current.extensionInstallStatus,
        selectedUserAgentPreset = current.selectedUserAgentPreset,
        appliedUserAgentLabel = current.appliedUserAgentLabel,
        customUserAgentInput = current.customUserAgentInput,
        cookieDomainInput = current.cookieDomainInput,
        logs = if (keepLogs) current.logs else emptyList(),
      )
    }
  }
}
