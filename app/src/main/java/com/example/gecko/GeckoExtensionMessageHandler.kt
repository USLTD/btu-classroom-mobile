package com.example.gecko

import com.example.model.CookieMetadata
import java.util.concurrent.ConcurrentLinkedQueue
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension

/**
 * Dedicated bidirectional native messaging handler bridging Kotlin and the bundled WebExtension.
 * Queues outbound commands until the WebExtension background port connects.
 */
class GeckoExtensionMessageHandler(
  private val listener: Listener,
) : WebExtension.MessageDelegate {

  interface Listener {
    fun onSessionDetected(sessionValue: String?, expirationDateEpochSeconds: Long?)
    fun onSessionReady(sessionValue: String, expirationDateEpochSeconds: Long?)
    fun onSessionCookieMissing()
    fun onSessionExpired(reason: String, expirationDateEpochSeconds: Long?)
    fun onBtuCallbackObserved()
    fun onAuthenticatedPageReached()
    fun onOAuthTokenCaptured(
      hasAuthorizationCode: Boolean,
      hasAccessToken: Boolean,
      tokenPayload: String?,
      oauthError: String?,
    )
    fun onUserAgentUpdated(modeId: String, hasOverride: Boolean)
    fun onDomainCookiesListed(
      domain: String,
      cookies: List<CookieMetadata>,
      rejected: Boolean,
      errorOrReason: String?,
    )
    fun onDomainCookiesDeleted(
      domain: String,
      deletedCount: Int,
      deletedNames: List<String>,
      rejected: Boolean,
      errorOrReason: String?,
    )
    fun onBtuCookiesDebugLogged(domain: String, cookies: List<CookieMetadata>, error: String?)
  }

  @Volatile
  private var controlPort: WebExtension.Port? = null
  private val pendingCommands = ConcurrentLinkedQueue<JSONObject>()

  override fun onMessage(
    nativeApp: String,
    message: Any,
    sender: WebExtension.MessageSender,
  ): GeckoResult<Any>? {
    if (nativeApp != NATIVE_APP_NAME) return null
    val json = message as? JSONObject ?: return null
    dispatchIncomingJson(json)
    return GeckoResult.fromValue(JSONObject().put("ack", true))
  }

  override fun onConnect(port: WebExtension.Port) {
    controlPort = port
    port.setDelegate(object : WebExtension.PortDelegate {
      override fun onPortMessage(message: Any, port: WebExtension.Port) {
        val json = message as? JSONObject ?: return
        dispatchIncomingJson(json)
      }

      override fun onDisconnect(port: WebExtension.Port) {
        if (controlPort === port) {
          controlPort = null
        }
      }
    })
    flushPendingCommands()
  }

  fun attachToExtension(extension: WebExtension) {
    extension.setMessageDelegate(this, NATIVE_APP_NAME)
  }

  fun queryBtuSession(expectAuthenticated: Boolean) {
    sendCommand(
      JSONObject()
        .put("action", "QUERY_BTU_SESSION")
        .put("expectAuthenticated", expectAuthenticated),
    )
  }

  fun setUserAgent(modeId: String, userAgent: String?) {
    sendCommand(
      JSONObject()
        .put("action", "SET_USER_AGENT")
        .put("modeId", modeId)
        .put("userAgent", userAgent ?: ""),
    )
  }

  fun listDomainCookies(domain: String) {
    sendCommand(
      JSONObject()
        .put("action", "LIST_DOMAIN_COOKIES")
        .put("domain", domain),
    )
  }

  fun deleteDomainCookies(domain: String) {
    sendCommand(
      JSONObject()
        .put("action", "DELETE_DOMAIN_COOKIES")
        .put("domain", domain),
    )
  }

  fun logBtuCookies() {
    sendCommand(JSONObject().put("action", "LOG_BTU_COOKIES"))
  }

  fun checkSessionExpiration() {
    sendCommand(JSONObject().put("action", "CHECK_SESSION_EXPIRATION"))
  }

  fun resetExtensionState() {
    sendCommand(JSONObject().put("action", "RESET_STATE"))
  }

  private fun sendCommand(command: JSONObject) {
    val port = controlPort
    if (port == null) {
      pendingCommands.add(command)
      return
    }
    try {
      port.postMessage(command)
    } catch (_: Throwable) {
      pendingCommands.add(command)
    }
  }

  private fun flushPendingCommands() {
    val port = controlPort ?: return
    while (true) {
      val next = pendingCommands.poll() ?: break
      try {
        port.postMessage(next)
      } catch (_: Throwable) {
        pendingCommands.add(next)
        break
      }
    }
  }

  internal fun dispatchIncomingJson(json: JSONObject) {
    when (json.optString("type")) {
      "SESSION_DETECTED" -> {
        val session = json.optString("session", "").ifBlank { null }
        val exp = json.optLongOrNull("expirationDate")
        listener.onSessionDetected(session, exp)
      }
      "SESSION_READY" -> {
        val session = json.optString("session", "")
        val exp = json.optLongOrNull("expirationDate")
        if (session.isBlank()) {
          listener.onSessionCookieMissing()
        } else {
          listener.onSessionReady(session, exp)
        }
      }
      "SESSION_COOKIE_MISSING" -> {
        listener.onSessionCookieMissing()
      }
      "SESSION_EXPIRED" -> {
        val reason = json.optString("reason", "expired")
        val exp = json.optLongOrNull("expirationDate")
        listener.onSessionExpired(reason, exp)
      }
      "BTU_CALLBACK_OBSERVED" -> {
        listener.onBtuCallbackObserved()
      }
      "AUTHENTICATED_PAGE_REACHED" -> {
        listener.onAuthenticatedPageReached()
      }
      "OAUTH_TOKEN_CAPTURED" -> {
        listener.onOAuthTokenCaptured(
          hasAuthorizationCode = json.optBoolean("hasAuthorizationCode", false),
          hasAccessToken = json.optBoolean("hasAccessToken", false),
          tokenPayload = json.optString("tokenPayload", "").ifBlank { null },
          oauthError = json.optString("oauthError", "").ifBlank { null },
        )
      }
      "USER_AGENT_UPDATED" -> {
        val modeId = json.optString("modeId", "DEFAULT")
        val hasOverride = json.optBoolean("hasOverride", false)
        listener.onUserAgentUpdated(modeId, hasOverride)
      }
      "DOMAIN_COOKIES_LIST" -> {
        val domain = json.optString("domain", "")
        val rejected = json.optBoolean("rejected", false)
        val err = json.optString("reason", "").ifBlank {
          json.optString("error", "").ifBlank { null }
        }
        val cookies = parseCookieMetadataArray(json.optJSONArray("cookies"))
        listener.onDomainCookiesListed(domain, cookies, rejected, err)
      }
      "DOMAIN_COOKIES_DELETED" -> {
        val domain = json.optString("domain", "")
        val rejected = json.optBoolean("rejected", false)
        val count = json.optInt("deletedCount", 0)
        val err = json.optString("reason", "").ifBlank {
          json.optString("error", "").ifBlank { null }
        }
        val namesArray = json.optJSONArray("deletedNames")
        val names = buildList {
          if (namesArray != null) {
            for (i in 0 until namesArray.length()) {
              val item = namesArray.optString(i, "")
              if (item.isNotBlank()) add(item)
            }
          }
        }
        listener.onDomainCookiesDeleted(domain, count, names, rejected, err)
      }
      "BTU_COOKIES_DEBUG_LOG" -> {
        val domain = json.optString("domain", "classroom.btu.edu.ge")
        val err = json.optString("error", "").ifBlank { null }
        val cookies = parseCookieMetadataArray(json.optJSONArray("cookies"))
        listener.onBtuCookiesDebugLogged(domain, cookies, err)
      }
    }
  }

  private fun parseCookieMetadataArray(array: JSONArray?): List<CookieMetadata> {
    if (array == null) return emptyList()
    return buildList {
      for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        add(
          CookieMetadata(
            name = obj.optString("name", ""),
            domain = obj.optString("domain", ""),
            path = obj.optString("path", "/"),
            secure = obj.optBoolean("secure", false),
            httpOnly = obj.optBoolean("httpOnly", false),
            sameSite = obj.optString("sameSite", "unspecified"),
            isSessionCookie = obj.optBoolean("isSessionCookie", true),
            expirationDateEpochSeconds = obj.optLongOrNull("expirationDate"),
            isExpired = obj.optBoolean("isExpired", false),
          ),
        )
      }
    }
  }

  private fun JSONObject.optLongOrNull(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return try {
      val v = getDouble(key).toLong()
      if (v > 0L) v else null
    } catch (_: Throwable) {
      null
    }
  }

  companion object {
    const val NATIVE_APP_NAME = "btucm_native"
  }
}
