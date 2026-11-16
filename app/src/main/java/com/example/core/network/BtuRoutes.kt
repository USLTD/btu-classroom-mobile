package com.example.core.network

import java.net.URI

object BtuRoutes {
  const val APPROVED_HOST = "classroom.btu.edu.ge"
  const val BASE_ORIGIN = "https://classroom.btu.edu.ge"
  const val SESSION_COOKIE_NAME = "argus_session"

  const val LOGIN_INDEX = "/ge/login/index"
  const val LOGIN_OAUTH = "/ge/login/oauth"
  const val LOGIN_LOGOUT = "/ge/login/logout"
  const val STUDENT_COURSES = "/ge/student/me/courses"
  const val STUDENT_SCHEDULE = "/ge/student/me/schedule"
  const val STUDENT_EXAMS = "/ge/exams/list"

  private const val BLOCKED_UPDATE_BALANCE_PATH = "/ge/student/me/updBalance"

  fun loginUrl(): String = "$BASE_ORIGIN$LOGIN_INDEX"

  fun coursesUrl(): String = "$BASE_ORIGIN$STUDENT_COURSES"

  fun scheduleUrl(): String = "$BASE_ORIGIN$STUDENT_SCHEDULE"

  fun logoutUrl(): String = "$BASE_ORIGIN$LOGIN_LOGOUT"

  fun courseDetailPath(courseId: String): String {
    val cleanId = courseId.trim().trim('/')
    return "/ge/student/me/course/index/$cleanId"
  }

  fun isBlockedMutationPath(path: String): Boolean {
    return path.contains(BLOCKED_UPDATE_BALANCE_PATH, ignoreCase = true)
  }

  /**
   * Resolves relative or absolute hrefs strictly against the approved BTU origin.
   * Rejects non-HTTPS schemes, external hosts, and restricted balance-update paths.
   */
  fun resolveSafeBtuUrl(
    hrefOrPath: String?,
    approvedOrigin: String = BASE_ORIGIN,
    allowLocalHttpForTesting: Boolean = false,
  ): String? {
    val raw = hrefOrPath?.trim() ?: return null
    if (raw.isEmpty() || raw.startsWith("#")) return null

    val lower = raw.lowercase()
    if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("mailto:") || lower.startsWith("tel:")) {
      return null
    }

    return try {
      val baseUri = URI(approvedOrigin)
      val resolved = if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
        URI(raw).normalize()
      } else {
        val normalizedPath = if (raw.startsWith("/")) raw else "/$raw"
        baseUri.resolve(normalizedPath).normalize()
      }

      val scheme = resolved.scheme?.lowercase() ?: return null
      val host = resolved.host?.lowercase() ?: return null
      val expectedHost = baseUri.host?.lowercase() ?: APPROVED_HOST

      val isValidScheme = if (allowLocalHttpForTesting && (host == "localhost" || host == "127.0.0.1")) {
        scheme == "http" || scheme == "https"
      } else {
        scheme == "https"
      }
      if (!isValidScheme) return null
      if (host != expectedHost) return null

      val path = resolved.rawPath ?: "/"
      if (isBlockedMutationPath(path)) return null

      resolved.toString()
    } catch (_: Throwable) {
      null
    }
  }
}
