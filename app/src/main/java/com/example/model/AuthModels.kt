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

data class AuthDiagnosticState(
  val geckoInitStatus: GeckoInitStatus = GeckoInitStatus.NOT_INITIALIZED,
  val extensionInstallStatus: ExtensionInstallStatus = ExtensionInstallStatus.NOT_INSTALLED,
  val anonymousSessionDetected: Boolean = false,
  val googleLoginStatus: GoogleLoginStatus = GoogleLoginStatus.IDLE,
  val btuCallbackDetected: Boolean = false,
  val sessionCaptured: Boolean = false,
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
