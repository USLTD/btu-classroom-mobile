package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BtuSessionRepository
import com.example.gecko.GeckoAuthManager
import com.example.model.AuthDiagnosticState
import com.example.model.LogLevel
import com.example.model.UserAgentPreset
import com.example.model.VerificationStatus
import com.example.network.BtuSessionVerifier
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoSession

class AuthViewModel(application: Application) : AndroidViewModel(application) {

  private val repository = BtuSessionRepository()
  private val verifier = BtuSessionVerifier()

  private val geckoManager: GeckoAuthManager = GeckoAuthManager(
    context = application,
    repository = repository,
    onAuthenticatedSessionReady = {
      verifySession()
    },
    onSessionExpired = { reason ->
      handleSessionExpiration(reason)
    },
  )

  val uiState: StateFlow<AuthDiagnosticState> = repository.state
  val activeGeckoSession: StateFlow<GeckoSession?> = geckoManager.activeSession

  fun startLogin() {
    if (repository.state.value.isBusy) return
    repository.dismissReauthPrompt()
    repository.setError(null)
    geckoManager.startLogin()
  }

  fun verifySession() {
    if (repository.state.value.isBusy) return
    val sessionValue = repository.currentSessionValue()
    if (sessionValue.isNullOrBlank()) {
      val msg = "No argus_session cookie available in memory. Tap Start Login first."
      repository.onVerificationResult(
        status = VerificationStatus.UNAUTHENTICATED,
        httpStatusCode = null,
        errorMessage = msg,
      )
      repository.appendLog("Verification skipped: no session in memory", LogLevel.WARNING)
      return
    }

    val wasCaptured = repository.state.value.sessionCaptured

    viewModelScope.launch {
      repository.setVerifying(true)
      repository.appendLog("Native HTTP verification started", LogLevel.INFO)
      val result = verifier.verify(sessionValue)
      result.httpStatusCode?.let { code ->
        repository.appendLog("HTTP $code", LogLevel.INFO)
      }
      val logLevel = when (result.status) {
        VerificationStatus.AUTHENTICATED -> LogLevel.SUCCESS
        VerificationStatus.CLOUDFLARE_BLOCKED,
        VerificationStatus.UNAUTHENTICATED,
        VerificationStatus.UNEXPECTED_RESPONSE -> LogLevel.WARNING
        VerificationStatus.NETWORK_ERROR -> LogLevel.ERROR
        else -> LogLevel.INFO
      }
      repository.appendLog(result.logSummary, logLevel)
      repository.onVerificationResult(
        status = result.status,
        httpStatusCode = result.httpStatusCode,
        errorMessage = result.errorMessage,
      )

      if (result.status == VerificationStatus.AUTHENTICATED) {
        geckoManager.releaseLoginSession()
      } else if (result.status == VerificationStatus.UNAUTHENTICATED && wasCaptured) {
        repository.markSessionExpiredRequiresReauth("server returned ${result.httpStatusCode ?: "unauthenticated"}")
      }
    }
  }

  private fun handleSessionExpiration(reason: String) {
    if (repository.state.value.isBusy) return
    val candidateSession = repository.lastKnownValidSessionValue()
    repository.setSessionRefreshing()
    repository.appendLog("Attempting automatic session refresh using last known valid session details...", LogLevel.INFO)

    viewModelScope.launch {
      // If a GeckoSession is currently active, ask it to reload the authenticated BTU page as well
      val reloadedInBrowser = geckoManager.attemptSilentSessionReload()
      if (reloadedInBrowser) {
        repository.appendLog("Triggered silent BTU session refresh inside active GeckoView", LogLevel.INFO)
      }

      if (!candidateSession.isNullOrBlank()) {
        val result = verifier.verify(candidateSession)
        result.httpStatusCode?.let { code ->
          repository.appendLog("Auto-refresh probe HTTP $code", LogLevel.INFO)
        }
        if (result.status == VerificationStatus.AUTHENTICATED) {
          repository.onSessionReady(candidateSession)
          repository.onVerificationResult(
            status = VerificationStatus.AUTHENTICATED,
            httpStatusCode = result.httpStatusCode,
            errorMessage = null,
          )
          repository.appendLog("Automatic session refresh succeeded; session remains valid on BTU", LogLevel.SUCCESS)
          return@launch
        }
      }

      repository.markSessionExpiredRequiresReauth(reason)
    }
  }

  fun checkSessionExpiration() {
    geckoManager.checkSessionExpiration()
    val exp = repository.state.value.sessionExpiresAtEpochSeconds
    if (exp != null && exp <= (System.currentTimeMillis() / 1000L)) {
      handleSessionExpiration("expiration_timestamp_passed")
    } else if (!repository.currentSessionValue().isNullOrBlank()) {
      verifySession()
    } else {
      repository.appendLog("No active argus_session to check for expiration", LogLevel.INFO)
    }
  }

  fun dismissReauthPrompt() {
    repository.dismissReauthPrompt()
  }

  fun reauthenticateNow() {
    repository.dismissReauthPrompt()
    resetAndStartLogin()
  }

  private fun resetAndStartLogin() {
    geckoManager.resetBrowserSession {
      repository.resetSessionState(keepLogs = true)
      geckoManager.startLogin()
    }
  }

  fun selectUserAgentPreset(preset: UserAgentPreset) {
    repository.setUserAgentSelection(preset)
    if (preset != UserAgentPreset.CUSTOM) {
      geckoManager.setUserAgent(preset, null)
    }
  }

  fun updateCustomUserAgentInput(value: String) {
    repository.setUserAgentSelection(UserAgentPreset.CUSTOM, value)
  }

  fun applyCurrentUserAgent() {
    val state = repository.state.value
    geckoManager.setUserAgent(state.selectedUserAgentPreset, state.customUserAgentInput)
  }

  fun updateCookieDomainInput(domain: String) {
    repository.setCookieDomainInput(domain)
  }

  fun listDomainCookies() {
    geckoManager.listCookiesForDomain(repository.state.value.cookieDomainInput)
  }

  fun deleteDomainCookies() {
    geckoManager.deleteCookiesForDomain(repository.state.value.cookieDomainInput)
  }

  fun logBtuCookiesDebug() {
    geckoManager.logBtuCookiesDebug()
  }

  fun resetSession() {
    if (repository.state.value.isBusy) return
    repository.appendLog("Resetting local GeckoView & native session state...", LogLevel.INFO)
    geckoManager.resetBrowserSession {
      repository.resetSessionState(keepLogs = true)
      repository.appendLog("Local session reset complete", LogLevel.INFO)
    }
  }

  fun requestRemoteLogout() {
    repository.setLogoutDialogVisible(true)
  }

  fun dismissLogoutDialog() {
    repository.setLogoutDialogVisible(false)
  }

  fun confirmRemoteLogout() {
    repository.setLogoutDialogVisible(false)
    if (repository.state.value.isBusy) return
    val currentCookie = repository.currentSessionValue()
    viewModelScope.launch {
      repository.setVerifying(true)
      repository.appendLog("Calling remote BTU logout (/ge/login/logout)...", LogLevel.INFO)
      val logoutResult = verifier.logout(currentCookie)
      repository.appendLog(
        logoutResult.detail,
        if (logoutResult.success) LogLevel.INFO else LogLevel.WARNING,
      )
      geckoManager.resetBrowserSession {
        repository.resetSessionState(keepLogs = true)
        repository.appendLog("Remote logout & local state cleared", LogLevel.INFO)
      }
    }
  }

  fun openBrowserModal() {
    if (activeGeckoSession.value != null) {
      repository.setBrowserModalVisible(true)
    }
  }

  fun closeBrowserModal() {
    repository.setBrowserModalVisible(false)
  }

  fun handleBrowserBack(): Boolean {
    return if (geckoManager.goBackInBrowser()) {
      true
    } else {
      repository.setBrowserModalVisible(false)
      true
    }
  }

  fun clearLogs() {
    repository.clearLogs()
  }

  override fun onCleared() {
    geckoManager.releaseLoginSession()
    super.onCleared()
  }
}
