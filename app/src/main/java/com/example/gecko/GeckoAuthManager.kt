package com.example.gecko

import android.content.Context
import com.example.data.BtuSessionRepository
import com.example.data.UrlSanitizer
import com.example.model.ExtensionInstallStatus
import com.example.model.GeckoInitStatus
import com.example.model.GoogleLoginStatus
import com.example.model.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
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
 * and native messaging for BTU Classroom authentication testing.
 */
class GeckoAuthManager(
  context: Context,
  private val repository: BtuSessionRepository,
  private val onAuthenticatedSessionReady: (String) -> Unit,
) {

  private val appContext = context.applicationContext

  private val _activeSession = MutableStateFlow<GeckoSession?>(null)
  val activeSession: StateFlow<GeckoSession?> = _activeSession.asStateFlow()

  private var installedExtension: WebExtension? = null
  private var controlPort: WebExtension.Port? = null
  private var lastLoggedPathKey: String? = null

  private val messageDelegate = object : WebExtension.MessageDelegate {
    override fun onMessage(
      nativeApp: String,
      message: Any,
      sender: WebExtension.MessageSender,
    ): GeckoResult<Any>? {
      if (nativeApp != NATIVE_APP_NAME) return null
      val json = message as? JSONObject ?: return null
      handleExtensionMessage(json)
      return GeckoResult.fromValue(JSONObject().put("ack", true))
    }

    override fun onConnect(port: WebExtension.Port) {
      controlPort = port
      port.setDelegate(object : WebExtension.PortDelegate {
        override fun onPortMessage(message: Any, port: WebExtension.Port) {
          val json = message as? JSONObject ?: return
          handleExtensionMessage(json)
        }

        override fun onDisconnect(port: WebExtension.Port) {
          if (controlPort === port) {
            controlPort = null
          }
        }
      })
    }
  }

  fun startLogin() {
    repository.setGeckoStatus(GeckoInitStatus.INITIALIZING)
    repository.setExtensionStatus(ExtensionInstallStatus.INSTALLING)
    repository.setBrowserModalVisible(true)
    lastLoggedPathKey = null

    val runtime = try {
      getOrCreateRuntime(appContext).also {
        repository.setGeckoStatus(GeckoInitStatus.READY)
        repository.appendLog("GeckoRuntime initialized", LogLevel.INFO)
      }
    } catch (t: Throwable) {
      val msg = "Failed to initialize GeckoRuntime: ${t.message ?: t.javaClass.simpleName}"
      repository.setGeckoStatus(GeckoInitStatus.FAILED, msg)
      repository.appendLog(msg, LogLevel.ERROR)
      return
    }

    ensureExtensionInstalled(runtime) { success ->
      if (!success) {
        return@ensureExtensionInstalled
      }
      openSessionAndNavigate(runtime)
    }
  }

  private fun ensureExtensionInstalled(
    runtime: GeckoRuntime,
    onDone: (Boolean) -> Unit,
  ) {
    val existing = installedExtension
    if (existing != null) {
      existing.setMessageDelegate(messageDelegate, NATIVE_APP_NAME)
      repository.setExtensionStatus(ExtensionInstallStatus.INSTALLED)
      onDone(true)
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
            onDone(false)
          } else {
            installedExtension = extension
            extension.setMessageDelegate(messageDelegate, NATIVE_APP_NAME)
            repository.setExtensionStatus(ExtensionInstallStatus.INSTALLED)
            repository.appendLog("WebExtension installed", LogLevel.SUCCESS)
            onDone(true)
          }
        },
        { throwable ->
          val msg = "WebExtension install failed: ${throwable?.message ?: "unknown error"}"
          repository.setExtensionStatus(ExtensionInstallStatus.FAILED, msg)
          repository.appendLog(msg, LogLevel.ERROR)
          onDone(false)
        },
      )
  }

  private fun openSessionAndNavigate(runtime: GeckoRuntime) {
    // Release any stale session before creating a fresh login session
    releaseCurrentSessionInternal(logRelease = false)

    val sessionSettings = GeckoSessionSettings.Builder()
      .usePrivateMode(false)
      .build()

    val session = GeckoSession(sessionSettings).apply {
      navigationDelegate = createNavigationDelegate()
      progressDelegate = createProgressDelegate()
    }

    installedExtension?.setMessageDelegate(messageDelegate, NATIVE_APP_NAME)

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
        // Always allow original BTU and Google OAuth navigation without altering parameters
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
          queryExtensionForSession(expectAuthenticated = true)
        } else if (success && currentStatus == GoogleLoginStatus.BTU_LOGIN_LOADED &&
          !repository.state.value.anonymousSessionDetected
        ) {
          queryExtensionForSession(expectAuthenticated = false)
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
        val rejectionMsg = "Google rejected the embedded GeckoView browser (disallowed_useragent). This authentication approach is incompatible with Google's current OAuth policy."
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
            queryExtensionForSession(expectAuthenticated = true)
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

  private fun handleExtensionMessage(json: JSONObject) {
    when (json.optString("type")) {
      "SESSION_DETECTED" -> {
        val sessionValue = json.optString("session", "")
        repository.onAnonymousSessionDetected(sessionValue.ifBlank { null })
      }
      "BTU_CALLBACK_OBSERVED" -> {
        if (!repository.state.value.btuCallbackDetected) {
          repository.onGoogleLoginStatus(
            status = GoogleLoginStatus.BTU_CALLBACK_OBSERVED,
            stageLabel = "BTU OAuth Callback",
            markCallbackDetected = true,
          )
          repository.appendLog("BTU callback observed (WebExtension)", LogLevel.INFO)
        }
      }
      "AUTHENTICATED_PAGE_REACHED" -> {
        if (repository.state.value.googleLoginStatus != GoogleLoginStatus.AUTHENTICATED_PAGE_REACHED) {
          repository.onGoogleLoginStatus(
            status = GoogleLoginStatus.AUTHENTICATED_PAGE_REACHED,
            stageLabel = "BTU Student Portal Reached",
          )
          repository.appendLog("Authenticated BTU page reached (WebExtension)", LogLevel.SUCCESS)
        }
      }
      "SESSION_READY" -> {
        val sessionValue = json.optString("session", "")
        if (sessionValue.isBlank()) {
          repository.onSessionCookieMissing()
        } else {
          repository.onSessionReady(sessionValue)
          onAuthenticatedSessionReady(sessionValue)
        }
      }
      "SESSION_COOKIE_MISSING" -> {
        repository.onSessionCookieMissing()
      }
    }
  }

  private fun queryExtensionForSession(expectAuthenticated: Boolean) {
    val port = controlPort ?: return
    try {
      port.postMessage(
        JSONObject()
          .put("action", "QUERY_BTU_SESSION")
          .put("expectAuthenticated", expectAuthenticated),
      )
    } catch (_: Throwable) {
      // Non-fatal; passive webNavigation/cookie listeners remain active
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

    try {
      controlPort?.postMessage(JSONObject().put("action", "RESET_STATE"))
    } catch (_: Throwable) {
    }

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

  companion object {
    private const val BTU_HOST = "classroom.btu.edu.ge"
    private const val BTU_LOGIN_URL = "https://classroom.btu.edu.ge/ge/login/index"
    private const val EXTENSION_LOCATION = "resource://android/assets/btucm_ext/"
    private const val EXTENSION_ID = "btucm-auth-poc@research.local"
    private const val NATIVE_APP_NAME = "btucm_native"

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
