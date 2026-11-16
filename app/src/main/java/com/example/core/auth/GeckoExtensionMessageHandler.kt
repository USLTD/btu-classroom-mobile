package com.example.core.auth

import java.util.concurrent.ConcurrentLinkedQueue
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.WebExtension

/**
 * Production native messaging handler bridging Kotlin and the hardened BTUCM WebExtension.
 * Handles only argus_session lifecycle and sanitized BTU navigation events.
 */
class GeckoExtensionMessageHandler(
  private val listener: Listener,
) : WebExtension.MessageDelegate {

  interface Listener {
    fun onSessionDetected(sessionValue: String?, expirationDateEpochSeconds: Long?)
    fun onSessionReady(sessionValue: String, expirationDateEpochSeconds: Long?)
    fun onSessionCookieMissing()
    fun onSessionExpired(reason: String, expirationDateEpochSeconds: Long?)
    fun onBtuCallbackObserved(hasError: Boolean)
    fun onAuthenticatedPageReached()
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
        val hasError = json.optBoolean("hasError", false)
        listener.onBtuCallbackObserved(hasError)
      }
      "AUTHENTICATED_PAGE_REACHED" -> {
        listener.onAuthenticatedPageReached()
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
