package com.example

import com.example.model.VerificationStatus
import com.example.network.BtuSessionVerifier
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BtuSessionVerifierTest {

  private lateinit var server: MockWebServer
  private lateinit var verifier: BtuSessionVerifier

  @Before
  fun setUp() {
    server = MockWebServer()
    server.start()
    val client = OkHttpClient.Builder()
      .followRedirects(false)
      .followSslRedirects(false)
      .connectTimeout(5, TimeUnit.SECONDS)
      .readTimeout(5, TimeUnit.SECONDS)
      .build()
    verifier = BtuSessionVerifier(
      coursesUrl = server.url("/ge/student/me/courses").toString(),
      logoutUrl = server.url("/ge/login/logout").toString(),
      client = client,
    )
  }

  @After
  fun tearDown() {
    server.shutdown()
  }

  @Test
  fun `verify returns AUTHENTICATED when HTTP 200 has Georgian course heading and course links`() = runTest {
    val html = """
      <html>
        <body>
          <table>
            <thead><tr><th>კურსის დასახელება</th></tr></thead>
            <tbody>
              <tr><td><a href="/ge/student/course/12345">Algorithms</a></td></tr>
            </tbody>
          </table>
        </body>
      </html>
    """.trimIndent()
    server.enqueue(MockResponse().setResponseCode(200).setBody(html))

    val result = verifier.verify("test_cookie_value_123")
    val recorded = server.takeRequest()

    assertEquals("argus_session=test_cookie_value_123", recorded.getHeader("Cookie"))
    assertEquals(VerificationStatus.AUTHENTICATED, result.status)
    assertEquals(200, result.httpStatusCode)
    assertNull(result.errorMessage)
  }

  @Test
  fun `verify returns UNEXPECTED_RESPONSE for generic HTTP 200 without course evidence`() = runTest {
    server.enqueue(MockResponse().setResponseCode(200).setBody("<html><body>OK</body></html>"))

    val result = verifier.verify("test_cookie_value_123")

    assertEquals(VerificationStatus.UNEXPECTED_RESPONSE, result.status)
    assertEquals(200, result.httpStatusCode)
  }

  @Test
  fun `verify returns UNAUTHENTICATED on 302 redirect to login without following redirect`() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(302)
        .addHeader("Location", "https://classroom.btu.edu.ge/ge/login/index"),
    )

    val result = verifier.verify("expired_cookie")

    assertEquals(VerificationStatus.UNAUTHENTICATED, result.status)
    assertEquals(302, result.httpStatusCode)
    assertEquals(1, server.requestCount)
  }

  @Test
  fun `verify returns UNAUTHENTICATED on HTTP 200 login page`() = runTest {
    val loginHtml = """
      <html><body><a href="/ge/login/oauth/">Login with Google</a></body></html>
    """.trimIndent()
    server.enqueue(MockResponse().setResponseCode(200).setBody(loginHtml))

    val result = verifier.verify("anon_cookie")

    assertEquals(VerificationStatus.UNAUTHENTICATED, result.status)
    assertEquals(200, result.httpStatusCode)
  }

  @Test
  fun `verify returns CLOUDFLARE_BLOCKED when cf-mitigated challenge header is present`() = runTest {
    server.enqueue(
      MockResponse()
        .setResponseCode(403)
        .addHeader("cf-mitigated", "challenge")
        .addHeader("Server", "cloudflare")
        .setBody("Just a moment..."),
    )

    val result = verifier.verify("test_cookie")

    assertEquals(VerificationStatus.CLOUDFLARE_BLOCKED, result.status)
    assertEquals(403, result.httpStatusCode)
  }

  @Test
  fun `verify returns NETWORK_ERROR when server is unreachable`() = runTest {
    server.shutdown()

    val result = verifier.verify("test_cookie")

    assertEquals(VerificationStatus.NETWORK_ERROR, result.status)
    assertNull(result.httpStatusCode)
    assertFalse(result.errorMessage.orEmpty().contains("test_cookie"))
  }

  @Test
  fun `logout calls logout endpoint with session cookie`() = runTest {
    server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/ge/login/index"))

    val logoutResult = verifier.logout("active_cookie_999")
    val recorded = server.takeRequest()

    assertTrue(logoutResult.success)
    assertEquals(302, logoutResult.httpStatusCode)
    assertEquals("/ge/login/logout", recorded.path)
    assertEquals("argus_session=active_cookie_999", recorded.getHeader("Cookie"))
  }
}
