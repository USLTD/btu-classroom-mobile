package com.example

import com.example.data.BtuSessionRepository
import com.example.gecko.GeckoExtensionMessageHandler
import com.example.model.CookieMetadata
import com.example.model.SessionExpiryStatus
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeckoExtensionMessageHandlerTest {

  @Test
  fun `dispatches OAuth token capture, session expiration, UA update, and cookie metadata safely`() {
    val repository = BtuSessionRepository()
    var expiredReason: String? = null
    var listedDomain: String? = null
    var listedCookies: List<CookieMetadata> = emptyList()
    var deletedCount = 0
    var debugLoggedCount = 0

    val handler = GeckoExtensionMessageHandler(
      object : GeckoExtensionMessageHandler.Listener {
        override fun onSessionDetected(sessionValue: String?, expirationDateEpochSeconds: Long?) {
          repository.onAnonymousSessionDetected(sessionValue, expirationDateEpochSeconds)
        }

        override fun onSessionReady(sessionValue: String, expirationDateEpochSeconds: Long?) {
          repository.onSessionReady(sessionValue, expirationDateEpochSeconds)
        }

        override fun onSessionCookieMissing() {
          repository.onSessionCookieMissing()
        }

        override fun onSessionExpired(reason: String, expirationDateEpochSeconds: Long?) {
          expiredReason = reason
        }

        override fun onBtuCallbackObserved() = Unit
        override fun onAuthenticatedPageReached() = Unit

        override fun onOAuthTokenCaptured(
          hasAuthorizationCode: Boolean,
          hasAccessToken: Boolean,
          tokenPayload: String?,
          oauthError: String?,
        ) {
          repository.onOAuthTokenCaptured(hasAuthorizationCode, hasAccessToken, tokenPayload, oauthError)
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
          listedDomain = domain
          listedCookies = cookies
          repository.onDomainCookiesListed(cookies)
        }

        override fun onDomainCookiesDeleted(
          domain: String,
          deletedCount: Int,
          deletedNames: List<String>,
          rejected: Boolean,
          errorOrReason: String?,
        ) {
          listedDomain = domain
          // Use local var outside via closure
          this@GeckoExtensionMessageHandlerTest.let {
            // handled via outer variable below
          }
          if (!rejected && errorOrReason == null) {
            repository.onDomainCookiesListed(emptyList())
          }
        }

        override fun onBtuCookiesDebugLogged(domain: String, cookies: List<CookieMetadata>, error: String?) {
          debugLoggedCount = cookies.size
        }
      },
    )

    // 1. OAuth token capture payload
    handler.dispatchIncomingJson(
      JSONObject()
        .put("type", "OAUTH_TOKEN_CAPTURED")
        .put("hasAuthorizationCode", true)
        .put("hasAccessToken", false)
        .put("tokenPayload", "secret_auth_code_999"),
    )
    assertTrue(repository.state.value.oauthTokenCaptured)
    assertFalse(repository.state.value.logs.any { it.message.contains("secret_auth_code_999") })

    // 2. Session ready
    handler.dispatchIncomingJson(
      JSONObject()
        .put("type", "SESSION_READY")
        .put("session", "btu_session_secret_xyz")
        .put("expirationDate", 1800000000L),
    )
    assertTrue(repository.state.value.sessionCaptured)
    assertEquals(SessionExpiryStatus.ACTIVE, repository.state.value.sessionExpiryStatus)
    assertEquals("btu_session_secret_xyz", repository.currentSessionValue())

    // 3. UA update
    handler.dispatchIncomingJson(
      JSONObject()
        .put("type", "USER_AGENT_UPDATED")
        .put("modeId", "FIREFOX_MOBILE")
        .put("hasOverride", true),
    )
    assertEquals("Firefox Android", repository.state.value.appliedUserAgentLabel)

    // 4. Domain cookie listing & BTU debug logging
    val cookiesArray = JSONArray().put(
      JSONObject()
        .put("name", "argus_session")
        .put("domain", "classroom.btu.edu.ge")
        .put("path", "/")
        .put("secure", true)
        .put("httpOnly", true)
        .put("sameSite", "lax")
        .put("isSessionCookie", false)
        .put("expirationDate", 1800000000L)
        .put("isExpired", false),
    )
    handler.dispatchIncomingJson(
      JSONObject()
        .put("type", "DOMAIN_COOKIES_LIST")
        .put("domain", "classroom.btu.edu.ge")
        .put("rejected", false)
        .put("cookies", cookiesArray),
    )
    assertEquals("classroom.btu.edu.ge", listedDomain)
    assertEquals(1, listedCookies.size)
    assertEquals("argus_session", listedCookies[0].name)

    handler.dispatchIncomingJson(
      JSONObject()
        .put("type", "BTU_COOKIES_DEBUG_LOG")
        .put("domain", "classroom.btu.edu.ge")
        .put("cookies", cookiesArray),
    )
    assertEquals(1, debugLoggedCount)

    // 5. Domain cookie deletion
    handler.dispatchIncomingJson(
      JSONObject()
        .put("type", "DOMAIN_COOKIES_DELETED")
        .put("domain", "classroom.btu.edu.ge")
        .put("rejected", false)
        .put("deletedCount", 1)
        .put("deletedNames", JSONArray().put("argus_session")),
    )
    assertTrue(repository.state.value.inspectedCookies.isEmpty())

    // 6. Session expiration & re-auth marking
    handler.dispatchIncomingJson(
      JSONObject()
        .put("type", "SESSION_EXPIRED")
        .put("reason", "expired"),
    )
    assertEquals("expired", expiredReason)

    repository.markSessionExpiredRequiresReauth("expired")
    assertFalse(repository.state.value.sessionCaptured)
    assertTrue(repository.state.value.isReauthPromptVisible)
    assertEquals(SessionExpiryStatus.EXPIRED_REAUTH_REQUIRED, repository.state.value.sessionExpiryStatus)
    assertNull(repository.currentSessionValue())
    assertEquals(0, deletedCount)
  }
}
