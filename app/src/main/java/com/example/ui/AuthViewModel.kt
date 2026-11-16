package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BtuSessionRepository
import com.example.gecko.GeckoAuthManager
import com.example.model.AuthDiagnosticState
import com.example.model.LogLevel
import com.example.model.VerificationStatus
import com.example.network.BtuSessionVerifier
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoSession

class AuthViewModel(application: Application) : AndroidViewModel(application) {

  private val repository = BtuSessionRepository()
  private val verifier = BtuSessionVerifier()

  private val geckoManager = GeckoAuthManager(
    context = application,
    repository = repository,
    onAuthenticatedSessionReady = {
      verifySession()
    },
  )

  val uiState: StateFlow<AuthDiagnosticState> = repository.state
  val activeGeckoSession: StateFlow<GeckoSession?> = geckoManager.activeSession

  fun startLogin() {
    if (repository.state.value.isBusy) return
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
        // Release GeckoView login session upon verified success so subsequent checks stay GeckoView-free
        geckoManager.releaseLoginSession()
      }
      // On failure, intentionally keep GeckoView modal open if currently displayed for inspection
    }
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
