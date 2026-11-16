package com.example.data

import java.net.URI

/**
 * Inspects navigation URIs in memory and extracts only non-sensitive metadata.
 * Query parameters (such as OAuth authorization codes or tokens) are never exposed in logs.
 */
object UrlSanitizer {

  data class ParsedNavigation(
    val host: String,
    val path: String,
    val hasDisallowedUserAgent: Boolean,
    val oauthErrorCode: String?,
  )

  private val SAFE_OAUTH_ERRORS = setOf(
    "disallowed_useragent",
    "access_denied",
    "invalid_request",
    "unauthorized_client",
    "unsupported_response_type",
    "invalid_scope",
    "server_error",
    "temporarily_unavailable",
    "org_internal",
    "admin_policy_enforced",
  )

  fun inspect(rawUrl: String?): ParsedNavigation? {
    if (rawUrl.isNullOrBlank()) return null
    return try {
      val uri = URI(rawUrl)
      val host = (uri.host ?: "").lowercase()
      val path = uri.path ?: "/"
      val query = uri.rawQuery.orEmpty()
      val lowerAll = rawUrl.lowercase()

      val disallowed = lowerAll.contains("disallowed_useragent")
      val extractedError = extractSafeErrorParam(query)
        ?: if (disallowed) "disallowed_useragent" else null

      ParsedNavigation(
        host = host,
        path = path,
        hasDisallowedUserAgent = disallowed,
        oauthErrorCode = extractedError,
      )
    } catch (_: Exception) {
      null
    }
  }

  private fun extractSafeErrorParam(rawQuery: String): String? {
    if (rawQuery.isBlank()) return null
    for (pair in rawQuery.split('&')) {
      val idx = pair.indexOf('=')
      if (idx <= 0) continue
      val key = pair.substring(0, idx).lowercase()
      val value = pair.substring(idx + 1).lowercase()
      if (key == "error" && value in SAFE_OAUTH_ERRORS) {
        return value
      }
    }
    return null
  }

  fun redactSensitiveTokens(text: String, activeSession: String? = null): String {
    var sanitized = text
    if (!activeSession.isNullOrBlank() && activeSession.length >= 6) {
      sanitized = sanitized.replace(activeSession, "<REDACTED_SESSION>")
    }
    sanitized = sanitized.replace(
      Regex("""(argus_session|code|access_token|id_token|refresh_token)=([^&\s;]+)""", RegexOption.IGNORE_CASE),
      "$1=<REDACTED>",
    )
    return sanitized
  }
}
