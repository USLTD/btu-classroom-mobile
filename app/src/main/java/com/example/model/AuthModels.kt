package com.example.model

enum class GeckoInitStatus(val label: String) {
  NOT_INITIALIZED("Not Initialized"),
  INITIALIZING("Initializing..."),
  READY("Initialized"),
  FAILED("Failed"),
}

enum class ExtensionInstallStatus(val label: String) {
  NOT_INSTALLED("Not Installed"),
  INSTALLING("Installing..."),
  INSTALLED("Installed"),
  FAILED("Failed"),
}

enum class GoogleLoginStatus(val label: String) {
  IDLE("Idle"),
  BTU_LOGIN_LOADED("BTU Login Page Loaded"),
  OAUTH_IN_PROGRESS("OAuth Navigation In Progress"),
  GOOGLE_LOGIN_PAGE_LOADED("Google Login Page Loaded"),
  REJECTED_DISALLOWED_USERAGENT("Rejected (disallowed_useragent)"),
  BTU_CALLBACK_OBSERVED("BTU Callback Observed"),
  AUTHENTICATED_PAGE_REACHED("Authenticated BTU Page Reached"),
  FAILED("Authentication / Network Failure"),
}

enum class VerificationStatus(val label: String) {
  IDLE("Not Verified"),
  VERIFYING("Verifying..."),
  AUTHENTICATED("AUTHENTICATED"),
  UNAUTHENTICATED("UNAUTHENTICATED"),
  CLOUDFLARE_BLOCKED("CLOUDFLARE_BLOCKED"),
  NETWORK_ERROR("NETWORK_ERROR"),
  UNEXPECTED_RESPONSE("UNEXPECTED_RESPONSE"),
}

enum class SessionExpiryStatus(val label: String) {
  NONE("No Active Session"),
  ACTIVE("Valid"),
  REFRESHING("Expired — Auto-Refreshing..."),
  EXPIRED_REAUTH_REQUIRED("Expired — Re-auth Required"),
}

enum class UserAgentPreset(val id: String, val label: String, val userAgentString: String?) {
  DEFAULT("DEFAULT", "Default (GeckoView)", null),
  FIREFOX_MOBILE(
    "FIREFOX_MOBILE",
    "Firefox Android",
    "Mozilla/5.0 (Android 14; Mobile; rv:136.0) Gecko/136.0 Firefox/136.0",
  ),
  CHROME_MOBILE(
    "CHROME_MOBILE",
    "Chrome Android",
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Mobile Safari/537.36",
  ),
  FIREFOX_DESKTOP(
    "FIREFOX_DESKTOP",
    "Firefox Desktop",
    "Mozilla/5.0 (X11; Linux x86_64; rv:136.0) Gecko/20100101 Firefox/136.0",
  ),
  CUSTOM("CUSTOM", "Custom UA", null),
}

enum class LogLevel {
  INFO,
  SUCCESS,
  WARNING,
  ERROR,
}

data class DiagnosticLogEntry(
  val id: Long,
  val timestamp: String,
  val message: String,
  val level: LogLevel = LogLevel.INFO,
)

/**
 * Redacted cookie metadata safe for UI and diagnostic logging (never stores cookie value).
 */
data class CookieMetadata(
  val name: String,
  val domain: String,
  val path: String,
  val secure: Boolean,
  val httpOnly: Boolean,
  val sameSite: String,
  val isSessionCookie: Boolean,
  val expirationDateEpochSeconds: Long?,
  val isExpired: Boolean,
)

data class AuthDiagnosticState(
  val geckoInitStatus: GeckoInitStatus = GeckoInitStatus.NOT_INITIALIZED,
  val extensionInstallStatus: ExtensionInstallStatus = ExtensionInstallStatus.NOT_INSTALLED,
  val anonymousSessionDetected: Boolean = false,
  val googleLoginStatus: GoogleLoginStatus = GoogleLoginStatus.IDLE,
  val btuCallbackDetected: Boolean = false,
  val oauthTokenCaptured: Boolean = false,
  val sessionCaptured: Boolean = false,
  val sessionExpiryStatus: SessionExpiryStatus = SessionExpiryStatus.NONE,
  val sessionExpiresAtEpochSeconds: Long? = null,
  val isReauthPromptVisible: Boolean = false,
  val selectedUserAgentPreset: UserAgentPreset = UserAgentPreset.DEFAULT,
  val appliedUserAgentLabel: String = UserAgentPreset.DEFAULT.label,
  val customUserAgentInput: String = "",
  val cookieDomainInput: String = "classroom.btu.edu.ge",
  val inspectedCookies: List<CookieMetadata> = emptyList(),
  val verificationStatus: VerificationStatus = VerificationStatus.IDLE,
  val lastHttpStatusCode: Int? = null,
  val errorMessage: String? = null,
  val isBrowserModalVisible: Boolean = false,
  val hasActiveBrowserSession: Boolean = false,
  val browserCanGoBack: Boolean = false,
  val browserLoadProgress: Int = 0,
  val browserStageLabel: String = "Idle",
  val isBusy: Boolean = false,
  val isLogoutDialogVisible: Boolean = false,
  val logs: List<DiagnosticLogEntry> = emptyList(),
)
