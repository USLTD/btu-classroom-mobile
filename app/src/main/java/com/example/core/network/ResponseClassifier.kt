package com.example.core.network

enum class VerificationStatus(val label: String) {
  IDLE("Not Verified"),
  VERIFYING("Verifying..."),
  AUTHENTICATED("AUTHENTICATED"),
  UNAUTHENTICATED("UNAUTHENTICATED"),
  CLOUDFLARE_BLOCKED("CLOUDFLARE_BLOCKED"),
  NETWORK_ERROR("NETWORK_ERROR"),
  UNEXPECTED_RESPONSE("UNEXPECTED_RESPONSE"),
}

sealed interface HttpFetchResult {
  data class SuccessHtml(
    val statusCode: Int,
    val html: String,
    val latencyMs: Long,
  ) : HttpFetchResult

  data class SessionExpired(
    val statusCode: Int?,
    val reason: String,
  ) : HttpFetchResult

  data class CloudflareBlocked(
    val statusCode: Int,
    val reason: String,
  ) : HttpFetchResult

  data class TemporarilyUnavailable(
    val statusCode: Int,
    val reason: String,
  ) : HttpFetchResult

  data class NetworkError(
    val reason: String,
  ) : HttpFetchResult

  data class UnexpectedResponse(
    val statusCode: Int?,
    val reason: String,
  ) : HttpFetchResult
}

data class VerificationResult(
  val status: VerificationStatus,
  val httpStatusCode: Int?,
  val errorMessage: String?,
  val logSummary: String,
  val html: String? = null,
)

data class LogoutResult(
  val success: Boolean,
  val httpStatusCode: Int?,
  val detail: String,
)

object ResponseClassifier {

  private const val AUTHENTICATED_COURSES_HEADING = "კურსის დასახელება"

  private val COURSE_LINK_REGEX = Regex(
    """href\s*=\s*["'][^"']*(?:/ge/student/me/course/index/|/ge/student/me/courses(?:/|\?)|/ge/student/(?!login\b|me/courses(?:["'#]|$))|/ge/courses?/|/course/)[^"']*["']""",
    RegexOption.IGNORE_CASE,
  )

  fun classifyHtmlFetch(
    statusCode: Int,
    locationHeader: String?,
    cfMitigatedHeader: String?,
    serverHeader: String?,
    contentTypeHeader: String?,
    body: String,
    latencyMs: Long,
  ): HttpFetchResult {
    // 1. Verify genuine HTTP 200 HTML first before Cloudflare heuristics so embedded challenge RUM scripts
    // on legitimate authenticated pages never cause false CLOUDFLARE_BLOCKED failures.
    if (statusCode == 200) {
      if (!isAllowedHtmlContentType(contentTypeHeader)) {
        return HttpFetchResult.UnexpectedResponse(
          statusCode = statusCode,
          reason = "Unexpected content type: ${contentTypeHeader ?: "unknown"}",
        )
      }
      if (isUnambiguousCloudflareInterstitial(body)) {
        return HttpFetchResult.CloudflareBlocked(
          statusCode = statusCode,
          reason = "Cloudflare challenge interstitial returned (HTTP 200).",
        )
      }
      if (looksLikeLoginPage(body)) {
        return HttpFetchResult.SessionExpired(
          statusCode = statusCode,
          reason = "Endpoint returned a login page instead of authenticated content.",
        )
      }
      return HttpFetchResult.SuccessHtml(
        statusCode = statusCode,
        html = body,
        latencyMs = latencyMs,
      )
    }

    // 2. Check Cloudflare block/challenge responses on error codes.
    if (isCloudflareBlocked(statusCode, cfMitigatedHeader, serverHeader, body)) {
      return HttpFetchResult.CloudflareBlocked(
        statusCode = statusCode,
        reason = "Cloudflare challenge or rate limit detected (HTTP $statusCode).",
      )
    }

    // 3. Classify redirects without following them.
    if (statusCode in setOf(301, 302, 303, 307, 308)) {
      val loc = locationHeader.orEmpty().lowercase()
      return if (loc.contains("/ge/login") || loc.contains("accounts.google.com") || loc.contains("login")) {
        HttpFetchResult.SessionExpired(
          statusCode = statusCode,
          reason = "Server redirected to login (HTTP $statusCode).",
        )
      } else {
        HttpFetchResult.UnexpectedResponse(
          statusCode = statusCode,
          reason = "Unexpected redirect HTTP $statusCode.",
        )
      }
    }

    // 4. Explicit auth failures.
    if (statusCode == 401 || statusCode == 403) {
      return HttpFetchResult.SessionExpired(
        statusCode = statusCode,
        reason = "Server rejected session (HTTP $statusCode).",
      )
    }

    // 5. Server maintenance / temporarily unavailable.
    if (statusCode in 500..599) {
      return HttpFetchResult.TemporarilyUnavailable(
        statusCode = statusCode,
        reason = "BTU Classroom returned server error HTTP $statusCode.",
      )
    }

    return HttpFetchResult.UnexpectedResponse(
      statusCode = statusCode,
      reason = "Unexpected HTTP $statusCode.",
    )
  }

  fun classifyCoursesVerification(
    statusCode: Int,
    locationHeader: String?,
    cfMitigatedHeader: String?,
    serverHeader: String?,
    contentTypeHeader: String?,
    body: String,
    latencyMs: Long,
  ): VerificationResult {
    // Check explicit authenticated courses table evidence first on HTTP 200
    if (statusCode == 200 && hasAuthenticatedCoursesEvidence(body)) {
      return VerificationResult(
        status = VerificationStatus.AUTHENTICATED,
        httpStatusCode = 200,
        errorMessage = null,
        logSummary = "AUTHENTICATED (courses heading and course links verified in ${latencyMs}ms)",
        html = body,
      )
    }

    return when (
      val classified = classifyHtmlFetch(
        statusCode = statusCode,
        locationHeader = locationHeader,
        cfMitigatedHeader = cfMitigatedHeader,
        serverHeader = serverHeader,
        contentTypeHeader = contentTypeHeader,
        body = body,
        latencyMs = latencyMs,
      )
    ) {
      is HttpFetchResult.SuccessHtml -> VerificationResult(
        status = VerificationStatus.UNEXPECTED_RESPONSE,
        httpStatusCode = classified.statusCode,
        errorMessage = "HTTP 200 received without expected Georgian courses table evidence.",
        logSummary = "UNEXPECTED_RESPONSE (HTTP 200 lacking course-page evidence)",
      )
      is HttpFetchResult.SessionExpired -> VerificationResult(
        status = VerificationStatus.UNAUTHENTICATED,
        httpStatusCode = classified.statusCode,
        errorMessage = classified.reason,
        logSummary = "UNAUTHENTICATED (${classified.statusCode ?: "local"})",
      )
      is HttpFetchResult.CloudflareBlocked -> VerificationResult(
        status = VerificationStatus.CLOUDFLARE_BLOCKED,
        httpStatusCode = classified.statusCode,
        errorMessage = classified.reason,
        logSummary = "CLOUDFLARE_BLOCKED (HTTP ${classified.statusCode})",
      )
      is HttpFetchResult.TemporarilyUnavailable -> VerificationResult(
        status = VerificationStatus.UNEXPECTED_RESPONSE,
        httpStatusCode = classified.statusCode,
        errorMessage = classified.reason,
        logSummary = "UNEXPECTED_RESPONSE (HTTP ${classified.statusCode})",
      )
      is HttpFetchResult.NetworkError -> VerificationResult(
        status = VerificationStatus.NETWORK_ERROR,
        httpStatusCode = null,
        errorMessage = classified.reason,
        logSummary = "NETWORK_ERROR (${classified.reason})",
      )
      is HttpFetchResult.UnexpectedResponse -> VerificationResult(
        status = VerificationStatus.UNEXPECTED_RESPONSE,
        httpStatusCode = classified.statusCode,
        errorMessage = classified.reason,
        logSummary = "UNEXPECTED_RESPONSE (${classified.statusCode ?: "unknown"})",
      )
    }
  }

  fun hasAuthenticatedCoursesEvidence(body: String): Boolean {
    val hasCoursesHeading = body.contains(AUTHENTICATED_COURSES_HEADING) ||
      body.contains("Course name", ignoreCase = true)
    val hasCourseLinks = COURSE_LINK_REGEX.containsMatchIn(body)
    return hasCoursesHeading && hasCourseLinks
  }

  private fun isAllowedHtmlContentType(contentTypeHeader: String?): Boolean {
    if (contentTypeHeader.isNullOrBlank()) return true
    val mediaType = contentTypeHeader.substringBefore(';').trim().lowercase()
    return mediaType.isEmpty() ||
      mediaType == "text/html" ||
      mediaType == "application/xhtml+xml" ||
      mediaType == "text/plain"
  }

  private fun isUnambiguousCloudflareInterstitial(body: String): Boolean {
    val lower = body.lowercase()
    return lower.contains("<title>just a moment...</title>") ||
      lower.contains("<title>attention required! | cloudflare</title>") ||
      lower.contains("id=\"cf-challenge-running\"") ||
      lower.contains("class=\"cf-browser-verification\"") ||
      (lower.contains("id=\"challenge-form\"") && lower.contains("cf_chl_opt"))
  }

  private fun isCloudflareBlocked(
    statusCode: Int,
    cfMitigatedHeader: String?,
    serverHeader: String?,
    body: String,
  ): Boolean {
    if (cfMitigatedHeader?.equals("challenge", ignoreCase = true) == true) {
      return true
    }

    val lowerBody = body.lowercase()
    val isCfServer = serverHeader?.contains("cloudflare", ignoreCase = true) == true

    if (statusCode in setOf(403, 429, 503)) {
      val hasChallengeBody =
        lowerBody.contains("cf-browser-verification") ||
          lowerBody.contains("cf_chl_opt") ||
          lowerBody.contains("/cdn-cgi/challenge-platform") ||
          lowerBody.contains("just a moment...") ||
          lowerBody.contains("attention required! | cloudflare")
      if (isCfServer || hasChallengeBody) {
        return true
      }
    }

    return statusCode == 200 && isUnambiguousCloudflareInterstitial(body)
  }

  fun looksLikeLoginPage(body: String): Boolean {
    val lower = body.lowercase()
    return lower.contains("/ge/login/oauth") ||
      lower.contains("/ge/login/index") ||
      lower.contains("accounts.google.com/o/oauth2") ||
      (lower.contains("google") && lower.contains("login"))
  }
}
