package com.example.gecko

import android.content.Context
import com.example.data.BtuSessionRepository
import com.example.data.UrlSanitizer
import com.example.model.CookieMetadata
import com.example.model.ExtensionInstallStatus
import com.example.model.GeckoInitStatus
import com.example.model.GoogleLoginStatus
import com.example.model.LogLevel
import com.example.model.UserAgentPreset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebRequestError

/**
 * Coordinates GeckoRuntime, GeckoSession lifecycle, built-in WebExtension installation,
 * bidirectional native messaging, User-Agent switching, and domain cookie management.
 */
class GeckoAuthManager(
  context: Context,
  private val repository: BtuSessionRepository,
  private val onAuthenticatedSessionReady: (String) -> Unit,
  private val onSessionExpired: (String) -> Unit,
) {

  private val appContext = context.applicationContext

  private val _activeSession = MutableStateFlow<GeckoSession?>(null)
  val activeSession: StateFlow<GeckoSession?> = _activeSession.asStateFlow()

  private var installedExtension: WebExtension? = null
  private var lastLoggedPathKey: String? = null

  private val messageHandler = GeckoExtensionMessageHandler(
    object : GeckoExtensionMessageHandler.Listener {
      override fun onSessionDetected(sessionValue: String?, expirationDateEpochSeconds: Long?) {
        repository.onAnonymousSessionDetected(sessionValue, expirationDateEpochSeconds)
      }

      override fun onSessionReady(sessionValue: String, expirationDateEpochSeconds: Long?) {
        repository.onSessionReady(sessionValue, expirationDateEpochSeconds)
        onAuthenticatedSessionReady(sessionValue)
      }

      override fun onSessionCookieMissing() {
        repository.onSessionCookieMissing()
      }

      override fun onSessionExpired(reason: String, expirationDateEpochSeconds: Long?) {
        repository.appendLog("Detected argus_session expiration ($reason)", LogLevel.WARNING)
        onSessionExpired(reason)
      }

      override fun onBtuCallbackObserved() {
        if (!repository.state.value.btuCallbackDetected) {
          repository.onGoogleLoginStatus(
            status = GoogleLoginStatus.BTU_CALLBACK_OBSERVED,
            stageLabel = "BTU OAuth Callback",
            markCallbackDetected = true,
          )
          repository.appendLog("BTU callback observed (WebExtension)", LogLevel.INFO)
        }
      }

      override fun onAuthenticatedPageReached() {
        if (repository.state.value.googleLoginStatus != GoogleLoginStatus.AUTHENTICATED_PAGE_REACHED) {
          repository.onGoogleLoginStatus(
            status = GoogleLoginStatus.AUTHENTICATED_PAGE_REACHED,
            stageLabel = "BTU Student Portal Reached",
          )
          repository.appendLog("Authenticated BTU page reached (WebExtension)", LogLevel.SUCCESS)
        }
      }

      override fun onOAuthTokenCaptured(
        hasAuthorizationCode: Boolean,
        hasAccessToken: Boolean,
        tokenPayload: String?,
        oauthError: String?,
      ) {
        repository.onOAuthTokenCaptured(
          hasAuthorizationCode = hasAuthorizationCode,
          hasAccessToken = hasAccessToken,
          tokenPayload = tokenPayload,
          oauthError = oauthError,
        )
      }

      override fun onUserAgentUpdated(modeId: String, hasOverride: Boolean) {
        repository.onUserAgentApplied(modeId, hasOverride)
      }

      override fun onDomainCookiesListed(
        domain: String,
        cookies: List<CookieMetadata>,
        rejected: Boolean,
        errorOrReason: String?,
      ) {
        if (rejected) {
          repository.appendLog("Cookie list blocked for $domain: ${errorOrReason ?: "restricted"}", LogLevel.WARNING)
          return
        }
        if (errorOrReason != null) {
          repository.appendLog("Cookie list error for $domain: $errorOrReason", LogLevel.ERROR)
          return
        }
        repository.onDomainCookiesListed(cookies)
        repository.appendLog("Listed ${cookies.size} cookie(s) for $domain (values redacted)", LogLevel.INFO)
      }

      override fun onDomainCookiesDeleted(
        domain: String,
        deletedCount: Int,
        deletedNames: List<String>,
        rejected: Boolean,
        errorOrReason: String?,
      ) {
        if (rejected) {
          repository.appendLog("Cookie deletion blocked for $domain: ${errorOrReason ?: "restricted"}", LogLevel.WARNING)
          return
        }
        if (errorOrReason != null) {
          repository.appendLog("Cookie deletion error for $domain: $errorOrReason", LogLevel.ERROR)
          return
        }
        repository.onDomainCookiesListed(emptyList())
        val namesSummary = if (deletedNames.isEmpty()) "none" else deletedNames.joinToString(", ")
        repository.appendLog("Deleted $deletedCount cookie(s) for $domain [$namesSummary]", LogLevel.INFO)
        if (domain.contains("btu.edu.ge", ignoreCase = true) && "argus_session" in deletedNames) {
          onSessionExpired("deleted")
        }
      }

      override fun onBtuCookiesDebugLogged(
        domain: String,
        cookies: List<CookieMetadata>,
        error: String?,
      ) {
        if (error != null) {
          repository.appendLog("BTU cookie debug failed: $error", LogLevel.ERROR)
          return
        }
        repository.onDomainCookiesListed(cookies)
        if (cookies.isEmpty()) {
          repository.appendLog("BTU cookie debug [$domain]: 0 cookies present", LogLevel.INFO)
          return
        }
        repository.appendLog("BTU cookie debug [$domain]: ${cookies.size} cookie(s) observed:", LogLevel.INFO)
        cookies.forEach { cookie ->
          val expiryText = formatCookieExpiry(cookie)
          repository.appendLog(
            "  • ${cookie.name} | domain=${cookie.domain} | path=${cookie.path} | secure=${cookie.secure} | httpOnly=${cookie.httpOnly} | sameSite=${cookie.sameSite} | $expiryText",
            if (cookie.isExpired) LogLevel.WARNING else LogLevel.INFO,
          )
        }
      }
    },
  )

  fun startLogin() {
    repository.setBrowserModalVisible(true)
    lastLoggedPathKey = null
    ensureRuntimeAndExtension { runtime, ok ->
      if (!ok || runtime == null) return@ensureRuntimeAndExtension
      syncUserAgentToExtension()
      openSessionAndNavigate(runtime)
    }
  }

  fun setUserAgent(preset: UserAgentPreset, customUa: String?) {
    repository.setUserAgentSelection(preset, customUa)
    val effectiveUa = when (preset) {
      UserAgentPreset.DEFAULT -> null
      UserAgentPreset.CUSTOM -> customUa?.trim()?.ifBlank { null }
      else -> preset.userAgentString
    }
    ensureRuntimeAndExtension { _, ok ->
      if (ok) {
        messageHandler.setUserAgent(preset.id, effectiveUa)
      }
    }
  }

  fun listCookiesForDomain(domain: String) {
    val cleanDomain = domain.trim().ifBlank { BTU_HOST }
    repository.setCookieDomainInput(cleanDomain)
    ensureRuntimeAndExtension { _, ok ->
      if (ok) {
        messageHandler.listDomainCookies(cleanDomain)
      }
    }
  }

  fun deleteCookiesForDomain(domain: String) {
    val cleanDomain = domain.trim().ifBlank { BTU_HOST }
    repository.setCookieDomainInput(cleanDomain)
    ensureRuntimeAndExtension { _, ok ->
      if (ok) {
        messageHandler.deleteDomainCookies(cleanDomain)
      }
    }
  }

  fun logBtuCookiesDebug() {
    ensureRuntimeAndExtension { _, ok ->
      if (ok) {
        messageHandler.logBtuCookies()
      }
    }
  }

  fun checkSessionExpiration() {
    ensureRuntimeAndExtension { _, ok ->
      if (ok) {
        messageHandler.checkSessionExpiration()
      }
    }
  }

  fun attemptSilentSessionReload(): Boolean {
    val session = _activeSession.value ?: return false
    session.loadUri(BTU_COURSES_URL)
    messageHandler.queryBtuSession(expectAuthenticated = true)
    return true
  }

  private fun ensureRuntimeAndExtension(onReady: (GeckoRuntime?, Boolean) -> Unit) {
    val existingRuntime = sharedRuntime
    val existingExt = installedExtension
    if (existingRuntime != null && existingExt != null) {
      messageHandler.attachToExtension(existingExt)
      repository.setGeckoStatus(GeckoInitStatus.READY)
      repository.setExtensionStatus(ExtensionInstallStatus.INSTALLED)
      onReady(existingRuntime, true)
      return
    }

    if (existingRuntime == null) {
      repository.setGeckoStatus(GeckoInitStatus.INITIALIZING)
    }
    repository.setExtensionStatus(ExtensionInstallStatus.INSTALLING)

    val runtime = try {
      val wasNull = sharedRuntime == null
      getOrCreateRuntime(appContext).also {
        repository.setGeckoStatus(GeckoInitStatus.READY)
        if (wasNull) {
          repository.appendLog("GeckoRuntime initialized", LogLevel.INFO)
        }
      }
    } catch (t: Throwable) {
      val msg = "Failed to initialize GeckoRuntime: ${t.message ?: t.javaClass.simpleName}"
      repository.setGeckoStatus(GeckoInitStatus.FAILED, msg)
      repository.appendLog(msg, LogLevel.ERROR)
      onReady(null, false)
      return
    }

    runtime.webExtensionController
      .ensureBuiltIn(EXTENSION_LOCATION, EXTENSION_ID)
      .accept(
        { extension ->
          if (extension == null) {
            val msg = "WebExtension installation returned null extension."
            repository.setExtensionStatus(ExtensionInstallStatus.FAILED, msg)
            repository.appendLog(msg, LogLevel.ERROR)
            onReady(runtime, false)
          } else {
            val wasNew = installedExtension == null
            installedExtension = extension
            messageHandler.attachToExtension(extension)
            repository.setExtensionStatus(ExtensionInstallStatus.INSTALLED)
            if (wasNew) {
              repository.appendLog("WebExtension installed", LogLevel.SUCCESS)
            }
            onReady(runtime, true)
          }
        },
        { throwable ->
          val msg = "WebExtension install failed: ${throwable?.message ?: "unknown error"}"
          repository.setExtensionStatus(ExtensionInstallStatus.FAILED, msg)
          repository.appendLog(msg, LogLevel.ERROR)
          onReady(runtime, false)
        },
      )
  }

  private fun syncUserAgentToExtension() {
    val currentState = repository.state.value
    val preset = currentState.selectedUserAgentPreset
    val effectiveUa = when (preset) {
      UserAgentPreset.DEFAULT -> null
      UserAgentPreset.CUSTOM -> currentState.customUserAgentInput.trim().ifBlank { null }
      else -> preset.userAgentString
    }
    messageHandler.setUserAgent(preset.id, effectiveUa)
  }

  private fun openSessionAndNavigate(runtime: GeckoRuntime) {
    releaseCurrentSessionInternal(logRelease = false)

    val sessionSettings = GeckoSessionSettings.Builder()
      .usePrivateMode(false)
      .build()

    val session = GeckoSession(sessionSettings).apply {
      navigationDelegate = createNavigationDelegate()
      progressDelegate = createProgressDelegate()
    }

    installedExtension?.let { messageHandler.attachToExtension(it) }

    session.open(runtime)
    _activeSession.value = session
    repository.setActiveBrowserSession(true)
    repository.onGoogleLoginStatus(
      status = GoogleLoginStatus.IDLE,
      stageLabel = "Loading BTU Login...",
    )
    repository.appendLog("Navigating to BTU login page", LogLevel.INFO)
    session.loadUri(BTU_LOGIN_URL)
  }

  private fun createNavigationDelegate(): GeckoSession.NavigationDelegate {
    return object : GeckoSession.NavigationDelegate {
      override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
        repository.setBrowserNavigationUi(canGoBack = canGoBack)
      }

      override fun onLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest,
      ): GeckoResult<AllowOrDeny> {
        inspectNavigationUri(request.uri, isCommitted = false)
        return GeckoResult.fromValue(AllowOrDeny.ALLOW)
      }

      override fun onLocationChange(
        session: GeckoSession,
        url: String?,
        perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
        hasUserGesture: Boolean,
      ) {
        inspectNavigationUri(url, isCommitted = true)
      }

      override fun onLoadError(
        session: GeckoSession,
        uri: String?,
        error: WebRequestError,
      ): GeckoResult<String>? {
        val parsed = UrlSanitizer.inspect(uri)
        val targetHost = parsed?.host?.ifBlank { "unknown" } ?: "unknown"
        val message = "Navigation failed on $targetHost (code=${error.code}, category=${error.category})"
        repository.onGoogleLoginStatus(
          status = GoogleLoginStatus.FAILED,
          stageLabel = "Network Error",
          errorMessage = message,
        )
        repository.appendLog(message, LogLevel.ERROR)
        return null
      }
    }
  }

  private fun createProgressDelegate(): GeckoSession.ProgressDelegate {
    return object : GeckoSession.ProgressDelegate {
      override fun onProgressChange(session: GeckoSession, progress: Int) {
        repository.setBrowserNavigationUi(progress = progress)
      }

      override fun onPageStop(session: GeckoSession, success: Boolean) {
        repository.setBrowserNavigationUi(progress = if (success) 100 else 0)
        val currentStatus = repository.state.value.googleLoginStatus
        if (success && currentStatus == GoogleLoginStatus.AUTHENTICATED_PAGE_REACHED &&
          !repository.state.value.sessionCaptured
        ) {
          messageHandler.queryBtuSession(expectAuthenticated = true)
        } else if (success && currentStatus == GoogleLoginStatus.BTU_LOGIN_LOADED &&
          !repository.state.value.anonymousSessionDetected
        ) {
          messageHandler.queryBtuSession(expectAuthenticated = false)
        }
      }
    }
  }

  private fun inspectNavigationUri(rawUrl: String?, isCommitted: Boolean) {
    val parsed = UrlSanitizer.inspect(rawUrl) ?: return
    val host = parsed.host
    val path = parsed.path

    if (parsed.hasDisallowedUserAgent || parsed.oauthErrorCode == "disallowed_useragent") {
      val key = "rejected:$host"
      if (lastLoggedPathKey != key) {
        lastLoggedPathKey = key
        val rejectionMsg = "Google rejected the embedded GeckoView browser (disallowed_useragent). Current User-Agent: ${repository.state.value.appliedUserAgentLabel}."
        repository.onGoogleLoginStatus(
          status = GoogleLoginStatus.REJECTED_DISALLOWED_USERAGENT,
          stageLabel = "Google Rejected (disallowed_useragent)",
          errorMessage = rejectionMsg,
        )
        repository.appendLog("Google rejected browser (disallowed_useragent)", LogLevel.ERROR)
      }
      return
    }

    if (parsed.oauthErrorCode != null && (host.endsWith("google.com") || host == BTU_HOST)) {
      val key = "oauth_err:${parsed.oauthErrorCode}"
      if (lastLoggedPathKey != key) {
        lastLoggedPathKey = key
        val errMsg = "OAuth reported error: ${parsed.oauthErrorCode}"
        repository.onGoogleLoginStatus(
          status = GoogleLoginStatus.FAILED,
          stageLabel = "OAuth Error (${parsed.oauthErrorCode})",
          errorMessage = errMsg,
        )
        repository.appendLog(errMsg, LogLevel.ERROR)
      }
      return
    }

    if (host == BTU_HOST) {
      when {
        path.startsWith("/ge/login/oauth") -> {
          val key = "btu_callback"
          if (lastLoggedPathKey != key) {
            lastLoggedPathKey = key
            repository.onGoogleLoginStatus(
              status = GoogleLoginStatus.BTU_CALLBACK_OBSERVED,
              stageLabel = "BTU OAuth Callback",
              markCallbackDetected = true,
            )
            repository.appendLog("BTU callback observed", LogLevel.INFO)
          }
        }
        path.startsWith("/ge/student/") -> {
          val key = "btu_authenticated"
          if (lastLoggedPathKey != key) {
            lastLoggedPathKey = key
            repository.onGoogleLoginStatus(
              status = GoogleLoginStatus.AUTHENTICATED_PAGE_REACHED,
              stageLabel = "BTU Student Portal Reached",
            )
            repository.appendLog("Authenticated BTU page reached", LogLevel.SUCCESS)
          }
          if (isCommitted) {
            messageHandler.queryBtuSession(expectAuthenticated = true)
          }
        }
        path.startsWith("/ge/login/index") || path == "/" -> {
          if (isCommitted) {
            val key = "btu_login_page"
            if (lastLoggedPathKey != key) {
              lastLoggedPathKey = key
              repository.onGoogleLoginStatus(
                status = GoogleLoginStatus.BTU_LOGIN_LOADED,
                stageLabel = "BTU Login Page",
              )
              repository.appendLog("BTU login page loaded", LogLevel.INFO)
            }
          }
        }
      }
      return
    }

    if (host == "accounts.google.com" || host.endsWith(".accounts.google.com")) {
      if (isCommitted) {
        val key = "google_login_loaded"
        if (lastLoggedPathKey != key) {
          lastLoggedPathKey = key
          repository.onGoogleLoginStatus(
            status = GoogleLoginStatus.GOOGLE_LOGIN_PAGE_LOADED,
            stageLabel = "Google Sign-In",
          )
          repository.appendLog("Google login page loaded", LogLevel.INFO)
        }
      } else {
        val key = "google_oauth_started"
        if (lastLoggedPathKey != key && lastLoggedPathKey != "google_login_loaded") {
          lastLoggedPathKey = key
          repository.onGoogleLoginStatus(
            status = GoogleLoginStatus.OAUTH_IN_PROGRESS,
            stageLabel = "Google OAuth Navigating...",
          )
          repository.appendLog("Google OAuth navigation started", LogLevel.INFO)
        }
      }
    }
  }

  fun goBackInBrowser(): Boolean {
    val session = _activeSession.value ?: return false
    return if (repository.state.value.browserCanGoBack) {
      session.goBack()
      true
    } else {
      false
    }
  }

  fun releaseLoginSession() {
    val hadSession = _activeSession.value != null
    releaseCurrentSessionInternal(logRelease = hadSession)
    repository.setBrowserModalVisible(false)
  }

  fun resetBrowserSession(onComplete: () -> Unit = {}) {
    releaseCurrentSessionInternal(logRelease = false)
    repository.setBrowserModalVisible(false)
    lastLoggedPathKey = null
    messageHandler.resetExtensionState()

    val runtime = sharedRuntime
    if (runtime == null) {
      onComplete()
      return
    }

    val flags = StorageController.ClearFlags.ALL
    runtime.storageController
      .clearDataFromBaseDomain("btu.edu.ge", flags)
      .then {
        runtime.storageController.clearDataFromHost(BTU_HOST, flags)
      }
      .accept(
        {
          repository.appendLog("GeckoView BTU session & cookies cleared", LogLevel.INFO)
          onComplete()
        },
        { throwable ->
          repository.appendLog(
            "GeckoView storage clear warning: ${throwable?.message ?: "unknown"}",
            LogLevel.WARNING,
          )
          onComplete()
        },
      )
  }

  private fun releaseCurrentSessionInternal(logRelease: Boolean) {
    val session = _activeSession.value
    if (session != null) {
      _activeSession.value = null
      repository.setActiveBrowserSession(false)
      try {
        session.navigationDelegate = null
        session.progressDelegate = null
        session.close()
      } catch (_: Throwable) {
      }
      if (logRelease) {
        repository.appendLog("GeckoView login session released", LogLevel.INFO)
      }
    } else {
      repository.setActiveBrowserSession(false)
    }
  }

  private fun formatCookieExpiry(cookie: CookieMetadata): String {
    if (cookie.isSessionCookie || cookie.expirationDateEpochSeconds == null) {
      return "session-cookie"
    }
    val formatted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
      .format(Date(cookie.expirationDateEpochSeconds * 1000L))
    return if (cookie.isExpired) "EXPIRED($formatted)" else "expires=$formatted"
  }

  companion object {
    private const val BTU_HOST = "classroom.btu.edu.ge"
    private const val BTU_LOGIN_URL = "https://classroom.btu.edu.ge/ge/login/index"
    private const val BTU_COURSES_URL = "https://classroom.btu.edu.ge/ge/student/me/courses"
    private const val EXTENSION_LOCATION = "resource://android/assets/btucm_ext/"
    private const val EXTENSION_ID = "btucm-auth-poc@research.local"

    @Volatile
    private var sharedRuntime: GeckoRuntime? = null

    fun getOrCreateRuntime(context: Context): GeckoRuntime {
      return sharedRuntime ?: synchronized(this) {
        sharedRuntime ?: run {
          val settings = GeckoRuntimeSettings.Builder()
            .javaScriptEnabled(true)
            .build()
          GeckoRuntime.create(context.applicationContext, settings).also {
            sharedRuntime = it
          }
        }
      }
    }
  }
}
