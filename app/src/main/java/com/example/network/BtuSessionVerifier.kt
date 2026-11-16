package com.example.network

import com.example.model.VerificationStatus
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class VerificationResult(
  val status: VerificationStatus,
  val httpStatusCode: Int?,
  val errorMessage: String?,
  val logSummary: String,
)

data class LogoutResult(
  val success: Boolean,
  val httpStatusCode: Int?,
  val detail: String,
)

/**
 * Independently verifies BTU Classroom authentication over OkHttp without GeckoView.
 * TLS certificate validation stays enabled, redirects are disabled, and no browser impersonation headers are sent.
 */
class BtuSessionVerifier(
  private val coursesUrl: String = DEFAULT_COURSES_URL,
  private val logoutUrl: String = DEFAULT_LOGOUT_URL,
  private val client: OkHttpClient = buildDefaultClient(),
) {

  suspend fun verify(sessionValue: String): VerificationResult = withContext(Dispatchers.IO) {
    if (sessionValue.isBlank()) {
      return@withContext VerificationResult(
        status = VerificationStatus.UNAUTHENTICATED,
        httpStatusCode = null,
        errorMessage = "No argus_session cookie captured to verify.",
        logSummary = "Verification skipped: no session in memory",
      )
    }

    val request = Request.Builder()
      .url(coursesUrl)
      .get()
      .header("Cookie", "$COOKIE_NAME=$sessionValue")
      .build()

    try {
      client.newCall(request).execute().use { response ->
        val code = response.code
        val location = response.header("Location")
        val cfMitigated = response.header("cf-mitigated")
        val serverHeader = response.header("Server")
        val body = response.body?.string().orEmpty()

        classifyResponse(
          statusCode = code,
          locationHeader = location,
          cfMitigatedHeader = cfMitigated,
          serverHeader = serverHeader,
          body = body,
        )
      }
    } catch (e: IOException) {
      val reason = e.javaClass.simpleName + (e.message?.let { ": $it" } ?: "")
      VerificationResult(
        status = VerificationStatus.NETWORK_ERROR,
        httpStatusCode = null,
        errorMessage = "Network error during verification ($reason)",
        logSummary = "NETWORK_ERROR ($reason)",
      )
    }
  }

  suspend fun logout(sessionValue: String?): LogoutResult = withContext(Dispatchers.IO) {
    val requestBuilder = Request.Builder()
      .url(logoutUrl)
      .get()
    if (!sessionValue.isNullOrBlank()) {
      requestBuilder.header("Cookie", "$COOKIE_NAME=$sessionValue")
    }
    try {
      client.newCall(requestBuilder.build()).execute().use { response ->
        val code = response.code
        LogoutResult(
          success = code in 200..399,
          httpStatusCode = code,
          detail = "Remote BTU logout returned HTTP $code",
        )
      }
    } catch (e: IOException) {
      val reason = e.javaClass.simpleName + (e.message?.let { ": $it" } ?: "")
      LogoutResult(
        success = false,
        httpStatusCode = null,
        detail = "Remote BTU logout failed ($reason)",
      )
    }
  }

  internal fun classifyResponse(
    statusCode: Int,
    locationHeader: String?,
    cfMitigatedHeader: String?,
    serverHeader: String?,
    body: String,
  ): VerificationResult {
    if (isCloudflareBlocked(statusCode, cfMitigatedHeader, serverHeader, body)) {
      return VerificationResult(
        status = VerificationStatus.CLOUDFLARE_BLOCKED,
        httpStatusCode = statusCode,
        errorMessage = "Cloudflare challenge or block detected (HTTP $statusCode).",
        logSummary = "CLOUDFLARE_BLOCKED (HTTP $statusCode)",
      )
    }

    if (statusCode in setOf(301, 302, 303, 307, 308)) {
      val loc = locationHeader.orEmpty().lowercase()
      return if (loc.contains("/ge/login") || loc.contains("accounts.google.com") || loc.contains("login")) {
        VerificationResult(
          status = VerificationStatus.UNAUTHENTICATED,
          httpStatusCode = statusCode,
          errorMessage = "Server redirected to login (HTTP $statusCode).",
          logSummary = "UNAUTHENTICATED (redirect HTTP $statusCode)",
        )
      } else {
        VerificationResult(
          status = VerificationStatus.UNEXPECTED_RESPONSE,
          httpStatusCode = statusCode,
          errorMessage = "Unexpected redirect HTTP $statusCode from courses endpoint.",
          logSummary = "UNEXPECTED_RESPONSE (redirect HTTP $statusCode)",
        )
      }
    }

    if (statusCode == 401) {
      return VerificationResult(
        status = VerificationStatus.UNAUTHENTICATED,
        httpStatusCode = statusCode,
        errorMessage = "Server rejected session (HTTP 401).",
        logSummary = "UNAUTHENTICATED (HTTP 401)",
      )
    }

    if (statusCode == 200) {
      val hasGeorgianCoursesHeading = body.contains(AUTHENTICATED_HEADING)
      val hasCourseDetailLinks = COURSE_LINK_REGEX.containsMatchIn(body)
      if (hasGeorgianCoursesHeading && hasCourseDetailLinks) {
        return VerificationResult(
          status = VerificationStatus.AUTHENTICATED,
          httpStatusCode = statusCode,
          errorMessage = null,
          logSummary = "AUTHENTICATED (courses heading and course links verified)",
        )
      }

      if (looksLikeLoginPage(body)) {
        return VerificationResult(
          status = VerificationStatus.UNAUTHENTICATED,
          httpStatusCode = statusCode,
          errorMessage = "Courses endpoint returned a login page instead of courses.",
          logSummary = "UNAUTHENTICATED (HTTP 200 login page)",
        )
      }

      return VerificationResult(
        status = VerificationStatus.UNEXPECTED_RESPONSE,
        httpStatusCode = statusCode,
        errorMessage = "HTTP 200 received without expected Georgian courses table evidence.",
        logSummary = "UNEXPECTED_RESPONSE (HTTP 200 lacking course-page evidence)",
      )
    }

    return VerificationResult(
      status = VerificationStatus.UNEXPECTED_RESPONSE,
      httpStatusCode = statusCode,
      errorMessage = "Unexpected HTTP $statusCode from courses endpoint.",
      logSummary = "UNEXPECTED_RESPONSE (HTTP $statusCode)",
    )
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
    val hasCfChallengeMarker =
      lowerBody.contains("cf-browser-verification") ||
        lowerBody.contains("cf_chl_opt") ||
        lowerBody.contains("/cdn-cgi/challenge-platform") ||
        lowerBody.contains("just a moment...") ||
        lowerBody.contains("attention required! | cloudflare")
    if (hasCfChallengeMarker) {
      return true
    }
    val isCfServer = serverHeader?.contains("cloudflare", ignoreCase = true) == true
    return isCfServer && statusCode in setOf(403, 429, 503)
  }

  private fun looksLikeLoginPage(body: String): Boolean {
    val lower = body.lowercase()
    return lower.contains("/ge/login/oauth") ||
      lower.contains("/ge/login/index") ||
      lower.contains("accounts.google.com/o/oauth2") ||
      (lower.contains("google") && lower.contains("login"))
  }

  companion object {
    const val DEFAULT_COURSES_URL = "https://classroom.btu.edu.ge/ge/student/me/courses"
    const val DEFAULT_LOGOUT_URL = "https://classroom.btu.edu.ge/ge/login/logout"
    private const val COOKIE_NAME = "argus_session"
    private const val AUTHENTICATED_HEADING = "კურსის დასახელება"

    private val COURSE_LINK_REGEX = Regex(
      """href\s*=\s*["'][^"']*(?:/ge/student/(?!login|me/courses\b)|/ge/course/|/course/)[^"']*["']""",
      RegexOption.IGNORE_CASE,
    )

    fun buildDefaultClient(): OkHttpClient {
      return OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    }
  }
}
